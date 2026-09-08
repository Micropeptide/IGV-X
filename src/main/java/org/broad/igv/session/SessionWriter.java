/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.session;

import org.broad.igv.feature.Bookmark;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeListItem;
import org.broad.igv.logging.*;
import org.broad.igv.feature.RegionOfInterest;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.lists.GeneList;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.AttributeManager;
import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.TrackFilter;
import org.broad.igv.ui.TrackFilterElement;
import org.broad.igv.ui.commandbar.GenomeListManager;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.Utilities;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Color;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jrobinso
 */
public class SessionWriter {

    static Logger log = LogManager.getLogger(SessionWriter.class);

    private Session session;
    private static int CURRENT_VERSION = 9;
    private File outputFile;
    private Document document;

    /**
     * Save the session as an XML document
     *
     * @param session
     * @param outputFile
     * @throws IOException
     */
    public void saveSession(Session session, File outputFile) throws IOException {

        if (session == null) {
            RuntimeException e = new RuntimeException("No session found to save!");
            log.error("Session Management Error", e);
        }

        this.session = session;

        if (outputFile == null) {
            log.error("Session Management Error: NULL outputFile");
        }

        String xmlString = createXmlFromSession(session, outputFile);

        Writer fileWriter = null;
        try {
            fileWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFile), "UTF8"));
            fileWriter.write(xmlString);
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }

        // IGV-X: optional companion metadata (.igvx.json). Never throws — the
        // session file itself is the source of truth.
        try {
            List<String> resourcePaths = new ArrayList<>();
            Map<String, Long> resourceMtimes = new LinkedHashMap<>();
            for (ResourceLocator rl : getResourceLocatorSet()) {
                if (rl != null && rl.getPath() != null) {
                    String rawPath = rl.getPath();
                    String p = rawPath;
                    if (isUseRelative(outputFile) && !FileUtils.isRemote(rawPath)) {
                        p = FileUtils.getRelativePath(outputFile.getAbsolutePath(), rawPath);
                    }
                    resourcePaths.add(p);
                    // IGV-X: record this resource's current mtime (keyed the same
                    // way it was just written above) so a later load can warn if
                    // the underlying file changed since this save -- e.g. it was
                    // silently reprocessed/regenerated upstream.
                    if (!FileUtils.isRemote(rawPath)) {
                        File f = new File(rawPath);
                        if (f.isAbsolute() && f.exists()) {
                            resourceMtimes.put(p, f.lastModified());
                        }
                    }
                }
            }
            SessionMetadata.write(outputFile,
                    GenomeManager.getInstance().getGenomeId(),
                    session.getLocusString(),
                    getResourceLocatorSet().size(),
                    resourcePaths,
                    isUseRelative(outputFile),
                    IGV.hasInstance() ? IGV.getInstance().getTrackHistory().toJson() : null,
                    resourceMtimes);
        } catch (Exception e) {
            log.warn("IGV-X: companion metadata write failed", e);
        }
    }


    public String createXmlFromSession(Session session, File outputFile) throws RuntimeException {

        this.session = session;
        this.outputFile = outputFile;

        try {

            // Create a DOM document
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = documentBuilder.newDocument();
            document.setStrictErrorChecking(true);

            // Global root element
            Element globalElement = document.createElement(SessionElement.SESSION);

            globalElement.setAttribute(SessionAttribute.VERSION, String.valueOf(CURRENT_VERSION));

            String genomeId = GenomeManager.getInstance().getGenomeId();
            if (genomeId != null) {
                // If genomeId is a file try to write it out as a relative path
                if ((new File(genomeId)).exists() && isUseRelative(outputFile)) {
                    genomeId = FileUtils.getRelativePath(outputFile.getAbsolutePath(), genomeId);
                }
                globalElement.setAttribute(SessionAttribute.GENOME, genomeId);
            }

            String locus = session.getLocusString();
            if (locus != null && !FrameManager.isGeneListMode()) {
                globalElement.setAttribute(SessionAttribute.LOCUS, locus);
            }

            String groupBy = session.getGroupByAttribute();
            if (groupBy != null) {
                globalElement.setAttribute(SessionAttribute.GROUP_TRACKS_BY, groupBy);
            }

            int nextAutoscaleGroup = session.getNextAutoscaleGroup();
            if (nextAutoscaleGroup > 1) {
                globalElement.setAttribute(SessionAttribute.NEXT_AUTOSCALE_GROUP, String.valueOf(nextAutoscaleGroup));
            }

            if (session.isRemoveEmptyPanels()) {
                globalElement.setAttribute("removeEmptyTracks", "true");
            }

            // Resource Files
            writeResources(outputFile, globalElement, document);

            // Panels
            writePanels(globalElement, document);

            // Panel layout
            writePanelLayout(globalElement, document);

            // Regions of Interest
            writeRegionsOfInterest(globalElement, document);

            // Bookmarks (IGV-X)
            writeBookmarks(globalElement, document);

            // Filter
            writeFilters(session, globalElement, document);

            if (FrameManager.isGeneListMode()) {
                writeGeneList(globalElement, document);
            }

            // Hidden attributes
            if (session.getHiddenAttributes() != null) {
                writeHiddenAttributes(session, globalElement, document);
            }

            document.appendChild(globalElement);

            // Transform document into XML
            return Utilities.getString(document);
        } catch (Exception e) {
            String message = "Error creating session.";
            log.error(message, e);
            throw new RuntimeException(e);
        }
    }


    private void writeFilters(Session session, Element globalElement, Document document) {
        TrackFilter trackFilter = session.getFilter();
        if (trackFilter != null) {

            Element filter = document.createElement(SessionElement.FILTER);

            filter.setAttribute(SessionAttribute.NAME, trackFilter.getName());

            if (IGV.getInstance().isFilterMatchAll()) {
                filter.setAttribute(SessionAttribute.FILTER_MATCH, "all");
            } else if (!IGV.getInstance().isFilterMatchAll()) {
                filter.setAttribute(SessionAttribute.FILTER_MATCH, "any");
            } else {    // Defaults to match all
                filter.setAttribute(SessionAttribute.FILTER_MATCH, "all");
            }

            if (IGV.getInstance().isFilterShowAllTracks()) {
                filter.setAttribute(SessionAttribute.FILTER_SHOW_ALL_TRACKS, "true");
            } else {    // Defaults
                filter.setAttribute(SessionAttribute.FILTER_SHOW_ALL_TRACKS, "false");
            }
            globalElement.appendChild(filter);

            // Process FilterElement elements
            Iterator iterator = session.getFilter().getFilterElements();
            while (iterator.hasNext()) {

                TrackFilterElement trackFilterElement = (TrackFilterElement) iterator.next();

                Element filterElementElement =
                        document.createElement(SessionElement.FILTER_ELEMENT);
                filterElementElement.setAttribute(SessionAttribute.ITEM,
                        trackFilterElement.getSelectedItem());
                filterElementElement.setAttribute(
                        SessionAttribute.OPERATOR,
                        trackFilterElement.getComparisonOperator().getValue());
                filterElementElement.setAttribute(SessionAttribute.VALUE,
                        trackFilterElement.getValue());
                filterElementElement.setAttribute(
                        SessionAttribute.BOOLEAN_OPERATOR,
                        trackFilterElement.getBooleanOperator().getValue());
                filter.appendChild(filterElementElement);
            }
        }
    }

    private void writeRegionsOfInterest(Element globalElement, Document document) {
        Collection<RegionOfInterest> regions = session.getAllRegionsOfInterest();
        if ((regions != null) && !regions.isEmpty()) {

            Element regionsElement = document.createElement(SessionElement.REGIONS);
            for (RegionOfInterest region : regions) {
                Element regionElement = document.createElement(SessionElement.REGION);
                regionElement.setAttribute(SessionAttribute.CHROMOSOME, region.getChr());
                regionElement.setAttribute(SessionAttribute.START_INDEX, String.valueOf(region.getStart()));
                regionElement.setAttribute(SessionAttribute.END_INDEX, String.valueOf(region.getEnd()));
                if (region.getDescription() != null) {
                    regionElement.setAttribute(SessionAttribute.DESCRIPTION, region.getDescription());
                }
                regionsElement.appendChild(regionElement);
            }
            globalElement.appendChild(regionsElement);
        }
    }

    /**
     * IGV-X: write persistent bookmarks to the session file.
     *
     * <pre>{@code
     * <Bookmarks>
     *   <Bookmark chromosome="chr1" start="100" end="500" label="promoter" color="#ff8800" highlighted="true"/>
     * </Bookmarks>
     * }</pre>
     */
    private void writeBookmarks(Element globalElement, Document document) {
        Collection<Bookmark> bookmarks = session.getAllBookmarks();
        if ((bookmarks != null) && !bookmarks.isEmpty()) {

            Element bookmarksElement = document.createElement(SessionElement.BOOKMARKS);
            for (Bookmark bookmark : bookmarks) {
                Element bookmarkElement = document.createElement(SessionElement.BOOKMARK);
                bookmarkElement.setAttribute(SessionAttribute.CHROMOSOME, bookmark.getChr());
                bookmarkElement.setAttribute(SessionAttribute.START_INDEX, String.valueOf(bookmark.getStart()));
                bookmarkElement.setAttribute(SessionAttribute.END_INDEX, String.valueOf(bookmark.getEnd()));
                if (bookmark.getLabel() != null) {
                    bookmarkElement.setAttribute(SessionAttribute.LABEL, bookmark.getLabel());
                }
                Color color = bookmark.getColor();
                if (color != null) {
                    bookmarkElement.setAttribute(SessionAttribute.COLOR, Bookmark.colorToString(color));
                }
                bookmarkElement.setAttribute(SessionAttribute.HIGHLIGHTED, String.valueOf(bookmark.isHighlighted()));
                bookmarksElement.appendChild(bookmarkElement);
            }
            globalElement.appendChild(bookmarksElement);
        }
    }

    private void writeHiddenAttributes(Session session, Element globalElement, Document document) {
        Element hiddenAttributes = document.createElement(SessionElement.HIDDEN_ATTRIBUTES);
        for (String attribute : session.getHiddenAttributes()) {
            Element regionElement = document.createElement(SessionElement.ATTRIBUTE);
            regionElement.setAttribute(SessionAttribute.NAME, attribute);
            hiddenAttributes.appendChild(regionElement);
        }
        globalElement.appendChild(hiddenAttributes);
    }

    private void writeGeneList(Element globalElement, Document document) {

        GeneList geneList = session.getCurrentGeneList();

        if (geneList != null) {

            Element geneListElement = document.createElement(SessionElement.GENE_LIST);
            geneListElement.setAttribute(SessionAttribute.NAME, geneList.getName());
            StringBuffer genes = new StringBuffer();
            for (String gene : geneList.getLoci()) {
                genes.append(gene);
                genes.append("\n");
            }
            geneListElement.setTextContent(genes.toString());
            globalElement.appendChild(geneListElement);


            // Now store the list of frames visible.  This seems redundant, but frame extent can be changed after
            // "gene list" definition, for example by zooming out or panning in a frame
            for (ReferenceFrame frame : FrameManager.getFrames()) {
                Element frameElement = document.createElement(SessionElement.FRAME);
                frameElement.setAttribute(SessionAttribute.NAME, frame.getName());
                frameElement.setAttribute(SessionAttribute.CHR, frame.getChrName());
                frameElement.setAttribute(SessionAttribute.START, String.valueOf(frame.getOrigin()));
                frameElement.setAttribute(SessionAttribute.END, String.valueOf(frame.getEnd()));

                geneListElement.appendChild(frameElement);

            }
        }
    }

    private void writeResources(File outputFile, Element globalElement, Document document) throws IOException {

        Collection<ResourceLocator> resourceLocators = getResourceLocatorSet();

        if ((resourceLocators != null) && !resourceLocators.isEmpty()) {

            Element filesElement = document.createElement(SessionElement.RESOURCES);

            for (ResourceLocator resourceLocator : resourceLocators) {
                if (resourceLocator.exists() || !(resourceLocator.getPath() == null)) {

                    //RESOURCE ELEMENT
                    Element dataFileElement = document.createElement(SessionElement.RESOURCE);

                    String resourcePath = relativizeIfApplicable(outputFile, resourceLocator.getPath());
                    dataFileElement.setAttribute(SessionAttribute.PATH, resourcePath);

                    //OPTIONAL ATTRIBUTES
                    if (resourceLocator.getName() != null) {
                        dataFileElement.setAttribute(SessionAttribute.NAME, resourceLocator.getName());
                    }
                    if (resourceLocator.getDBUrl() != null) {
                        dataFileElement.setAttribute(SessionAttribute.SERVER_URL, resourceLocator.getDBUrl());
                    }
                    if (resourceLocator.getTrackInfoURL() != null) {
                        dataFileElement.setAttribute(SessionAttribute.HYPERLINK, resourceLocator.getTrackInfoURL());
                    }
                    if (resourceLocator.getFeatureInfoURL() != null) {
                        dataFileElement.setAttribute(SessionAttribute.FEATURE_URL, resourceLocator.getFeatureInfoURL());
                    }
                    if (resourceLocator.getDescription() != null) {
                        dataFileElement.setAttribute(SessionAttribute.DESCRIPTION, resourceLocator.getDescription());
                    }
                    if (resourceLocator.format != null) {
                        dataFileElement.setAttribute(SessionAttribute.TYPE, resourceLocator.format);
                    }
                    if (resourceLocator.getIndexPath() != null) {
                        dataFileElement.setAttribute(SessionAttribute.INDEX, relativizeIfApplicable(outputFile, resourceLocator.getIndexPath()));
                    }
                    if (resourceLocator.getCoverage() != null) {
                        dataFileElement.setAttribute(SessionAttribute.COVERAGE, relativizeIfApplicable(outputFile, resourceLocator.getCoverage()));
                    }
                    if (resourceLocator.getMappingPath() != null) {
                        dataFileElement.setAttribute(SessionAttribute.MAPPING, relativizeIfApplicable(outputFile, resourceLocator.getMappingPath()));
                    }
                    if (resourceLocator.getTrackLine() != null) {
                        dataFileElement.setAttribute(SessionAttribute.TRACK_LINE, resourceLocator.getTrackLine());
                    }
                    filesElement.appendChild(dataFileElement);
                }
            }
            globalElement.appendChild(filesElement);
        }
    }

    private boolean isUseRelative(File outputFile) {
        return outputFile != null &&
                PreferencesManager.getPreferences().getAsBoolean(Constants.SESSION_RELATIVE_PATH);
    }

    /**
     * IGV-X: the single "should this path be relativized, and if so how"
     * check shared by every path-bearing session attribute this writer emits
     * (Resource/@path, Track/@id -- including nested Track elements inside a
     * merged/combined track -- and index/coverage/mapping). Having one
     * shared method means an edge case discovered for one of them (the "."
     * coverage/mapping sentinel, a non-path track id like the synthetic
     * "Reference sequence" track, a remote URL) is automatically handled for
     * all the others too, instead of each call site needing its own,
     * separately-maintained copy of the same guards.
     */
    private String relativizeIfApplicable(File outputFile, String path) {
        if (path == null || path.equals(".") || FileUtils.isRemote(path) || !new File(path).isAbsolute()) {
            return path;
        }
        return isUseRelative(outputFile) ? FileUtils.getRelativePath(outputFile.getAbsolutePath(), path) : path;
    }

    /**
     * Recursively relativize the "id" attribute of any nested &lt;Track&gt;
     * child elements (e.g. a MergedTracks' member tracks), which
     * {@link org.broad.igv.track.Track#marshalXML} writes as an absolute
     * path with no way to relativize it itself (it has no output-file
     * context).
     */
    private void relativizeNestedTrackIds(Element trackElement, File outputFile) {
        NodeList children = trackElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "Track".equals(child.getNodeName())) {
                Element childElement = (Element) child;
                String childId = childElement.getAttribute("id");
                if (childId != null && !childId.isEmpty()) {
                    childElement.setAttribute("id", relativizeIfApplicable(outputFile, childId));
                }
                relativizeNestedTrackIds(childElement, outputFile);
            }
        }
    }

    private void writePanels(Element globalElement, Document document) throws DOMException {

        for (TrackPanel trackPanel : IGV.getInstance().getTrackPanels()) {

            // TODO -- loop through panels groups, rather than skipping groups to tracks

            List<Track> tracks = trackPanel.getTracks();
            if ((tracks != null) && !tracks.isEmpty()) {

                Element panelElement = document.createElement(SessionElement.PANEL);
                panelElement.setAttribute("name", trackPanel.getName());
                panelElement.setAttribute("height", String.valueOf(trackPanel.getHeight()));
                panelElement.setAttribute("width", String.valueOf(trackPanel.getWidth()));

                for (Track track : tracks) {

                    Element element = document.createElement("Track");
                    element.setAttribute("clazz", SessionElement.getXMLClassName(track.getClass()));

                    String id = relativizeIfApplicable(outputFile, track.getId());

                    // IGV-X: marshalXML (below) unconditionally re-sets "id" from the
                    // track's own raw (always-absolute) id field, which clobbers the
                    // relative id computed above. Set it again afterward so the
                    // relative path actually survives into the written XML -- this
                    // was silently defeating relative-path saves for every track's
                    // Track/@id (the Resource/@path attribute has no such collision
                    // and was already correct).
                    element.setAttribute("id", id);
                    track.marshalXML(document, element);
                    element.setAttribute("id", id);

                    // A merged/combined track (MergedTracks) marshals its member
                    // tracks as nested <Track> child elements of its own, via the
                    // same Track.marshalXML(Document, Element) that has no
                    // output-file context to relativize against -- so their ids
                    // come out absolute no matter what. Fix those up here, where
                    // the output file IS known, exactly like the top-level id above.
                    relativizeNestedTrackIds(element, outputFile);

                    if (track.isNumeric() && track.getDataRange() != null) {
                        Element dataRangeElement = document.createElement(SessionElement.DATA_RANGE);
                        track.getDataRange().marshalXML(document, dataRangeElement);
                        element.appendChild(dataRangeElement);
                    }

                    panelElement.appendChild(element);

                }

                globalElement.appendChild(panelElement);
            }
        }
    }

    private void writePanelLayout(Element globalElement, Document document) {

        double[] dividerFractions = IGV.getInstance().getMainPanel().getDividerFractions();
        if (dividerFractions.length > 0) {

            Element panelLayout = document.createElement(SessionElement.PANEL_LAYOUT);
            globalElement.appendChild(panelLayout);

            StringBuffer locString = new StringBuffer();
            locString.append(dividerFractions[0]);
            for (int i = 1; i < dividerFractions.length; i++) {
                locString.append("," + dividerFractions[i]);
            }
            panelLayout.setAttribute("dividerFractions", locString.toString());
        }
    }

    /**
     * @return A set of the load data files.
     */
    public Collection<ResourceLocator> getResourceLocatorSet() {

        Collection<ResourceLocator> locators = new ArrayList();

        Collection<ResourceLocator> currentTrackFileLocators =
                IGV.getInstance().getDataResourceLocators();

        if (currentTrackFileLocators != null) {

            // Filter data files that are included in genome annotations
            final Genome currentGenome = GenomeManager.getInstance().getCurrentGenome();
            if(currentGenome != null) {
                List<ResourceLocator> genomeResources = currentGenome.getAnnotationResources();
                Set<String> absoluteGenomeAnnotationPaths = genomeResources == null ? Collections.emptySet() :
                        genomeResources.stream().map(rl -> rl.getPath()).collect(Collectors.toSet());

                for (ResourceLocator locator : currentTrackFileLocators) {
                    if (!absoluteGenomeAnnotationPaths.contains(locator.getPath())) {
                        locators.add(locator);
                    }
                }
            }
        }

        Collection<ResourceLocator> loadedAttributeResources =
                AttributeManager.getInstance().getLoadedResources();

        if (loadedAttributeResources != null) {
            for (ResourceLocator attributeLocator : loadedAttributeResources) {
                locators.add(attributeLocator);
            }
        }

        return locators;
    }

}

