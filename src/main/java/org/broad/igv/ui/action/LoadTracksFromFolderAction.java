/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X contributors
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.ui.action;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.TrackFolderScanner;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * IGV-X batch track import (Runtian feature request, 2026-08-10): pick a
 * folder, scan it (recursively, subfolders included) for loadable track files,
 * show a chooser dialog with type filter + load-all, and load the selected
 * files as tracks.
 */
public class LoadTracksFromFolderAction extends MenuAction {

    private static final Logger log = LogManager.getLogger(LoadTracksFromFolderAction.class);
    private final IGV igv;

    public LoadTracksFromFolderAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        IGVPreferences prefs = PreferencesManager.getPreferences();
        File dir = FileDialogUtils.chooseDirectory("Choose a folder of track files", prefs.getLastTrackDirectory());
        if (dir == null) {
            return;
        }
        prefs.setLastTrackDirectory(dir);
        igv.resetStatusMessage();

        final List<TrackFolderScanner.TrackFile> found = TrackFolderScanner.scan(dir);
        if (found.isEmpty()) {
            JOptionPane.showMessageDialog(igv.getMainFrame(),
                    "No track files found in:\n" + dir.getAbsolutePath() +
                            "\n\nIGV-X looks for files with these extensions: " +
                            String.join(", ", TrackFolderScanner.TRACK_EXTENSIONS),
                    "No track files", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        TrackChooserDialog dialog = new TrackChooserDialog(igv.getMainFrame(), dir, found);
        dialog.setVisible(true);
        final List<File> selected = dialog.getSelectedFiles();
        if (selected == null || selected.isEmpty()) {
            return;
        }
        final List<ResourceLocator> locators = ResourceLocator.getLocators(selected);
        igv.addToRecentUrls(locators);
        LongRunningTask.submit(() -> {
            try {
                igv.loadTracks(locators);
            } catch (Exception ex) {
                log.error("Error loading tracks from folder", ex);
            }
        });
    }

    /**
     * Modal chooser: checkbox table of scanned files, filter by type,
     * Select All / Clear, Load / Cancel.
     */
    static class TrackChooserDialog extends JDialog {

        private final List<TrackFolderScanner.TrackFile> allFiles;
        private final JTable table;
        private final TrackTableModel model;
        private final JComboBox<String> typeFilter;
        private List<File> selectedFiles = new ArrayList<>();
        private boolean ok = false;

        TrackChooserDialog(Frame owner, File root, List<TrackFolderScanner.TrackFile> files) {
            super(owner, "Load Tracks from Folder", true);
            this.allFiles = files;
            setLayout(new BorderLayout());

            // Header: folder + count
            JLabel header = new JLabel("<html><b>" + root.getAbsolutePath() + "</b><br>" +
                    files.size() + " track files found (subfolders included)</html>");
            header.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
            add(header, BorderLayout.NORTH);

            // Toolbar: type filter + select all/clear
            model = new TrackTableModel(files);
            JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            toolPanel.add(new JLabel("Filter by type:"));
            typeFilter = new JComboBox<>();
            typeFilter.addItem("All types");
            for (String t : TrackFolderScanner.typesIn(files)) {
                typeFilter.addItem(t);
            }
            typeFilter.addActionListener(e -> model.applyFilter((String) typeFilter.getSelectedItem()));
            toolPanel.add(typeFilter);
            JButton selectAll = new JButton("Select All");
            selectAll.addActionListener(e -> model.setAll(true));
            JButton clear = new JButton("Clear");
            clear.addActionListener(e -> model.setAll(false));
            toolPanel.add(selectAll);
            toolPanel.add(clear);
            toolPanel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            add(toolPanel, BorderLayout.CENTER);

            table = new JTable(model);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(1).setPreferredWidth(480);
            table.getColumnModel().getColumn(2).setMaxWidth(90);
            table.setRowHeight(20);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(760, 420));
            scroll.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            add(scroll, BorderLayout.SOUTH);

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton load = new JButton("Load Selected");
            load.addActionListener(e -> {
                ok = true;
                selectedFiles = model.getSelectedFiles();
                dispose();
            });
            JButton loadAll = new JButton("Load All");
            loadAll.addActionListener(e -> {
                ok = true;
                selectedFiles = new ArrayList<>();
                for (TrackFolderScanner.TrackFile tf : allFiles) {
                    selectedFiles.add(tf.file);
                }
                dispose();
            });
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(e -> dispose());
            buttonPanel.add(load);
            buttonPanel.add(loadAll);
            buttonPanel.add(cancel);
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
            add(buttonPanel, BorderLayout.EAST);

            pack();
            setLocationRelativeTo(owner);
        }

        List<File> getSelectedFiles() {
            return ok ? selectedFiles : null;
        }
    }

    static class TrackTableModel extends AbstractTableModel {
        private final List<TrackFolderScanner.TrackFile> all;
        private final boolean[] selected;
        private List<TrackFolderScanner.TrackFile> visible;

        TrackTableModel(List<TrackFolderScanner.TrackFile> files) {
            this.all = files;
            this.selected = new boolean[files.size()];
            this.visible = files;
        }

        void applyFilter(String type) {
            visible = new ArrayList<>();
            for (TrackFolderScanner.TrackFile tf : all) {
                if (type == null || "All types".equals(type) || type.equals(tf.type)) {
                    visible.add(tf);
                }
            }
            fireTableDataChanged();
        }

        void setAll(boolean value) {
            for (int i = 0; i < all.size(); i++) {
                selected[i] = value;
            }
            fireTableDataChanged();
        }

        List<File> getSelectedFiles() {
            List<File> result = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (selected[i]) {
                    result.add(all.get(i).file);
                }
            }
            return result;
        }

        @Override
        public int getRowCount() {
            return visible.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return "";
                case 1:
                    return "File";
                default:
                    return "Type";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TrackFolderScanner.TrackFile tf = visible.get(rowIndex);
            int idx = all.indexOf(tf);
            switch (columnIndex) {
                case 0:
                    return selected[idx];
                case 1:
                    return tf.file.getAbsolutePath();
                default:
                    return tf.type;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                TrackFolderScanner.TrackFile tf = visible.get(rowIndex);
                int idx = all.indexOf(tf);
                selected[idx] = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
