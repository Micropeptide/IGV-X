/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X
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


package org.broad.igv.ui.panel;

import org.broad.igv.feature.Bookmark;
import org.broad.igv.ui.IGV;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * IGV-X: dialog to view, jump to, highlight, and delete persistent bookmarks.
 * Bookmarks are stored per-chromosome in the session and serialized into
 * session files, so they survive reload.
 */
public class BookmarkManagerDialog extends JDialog {

    private static BookmarkManagerDialog instance;

    private final BookmarkTableModel tableModel;
    private final JTable table;

    public static synchronized BookmarkManagerDialog getInstance(Frame parent) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new BookmarkManagerDialog(parent);
        }
        instance.reload();
        return instance;
    }

    public static synchronized void destroyInstance() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    private BookmarkManagerDialog(Frame parent) {
        super(parent, "Bookmarks", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        tableModel = new BookmarkTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(240);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(640, 320));

        JButton viewButton = new JButton("Jump to");
        viewButton.addActionListener(e -> jumpToSelected());

        JButton highlightButton = new JButton("Toggle Highlight");
        highlightButton.addActionListener(e -> toggleHighlightSelected());

        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteSelected());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(viewButton);
        buttonPanel.add(highlightButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    void reload() {
        tableModel.reload(IGV.getInstance().getSession().getAllBookmarks());
    }

    private Bookmark getSelectedBookmark() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            return null;
        }
        return tableModel.getBookmarkAt(row);
    }

    private void jumpToSelected() {
        Bookmark bookmark = getSelectedBookmark();
        if (bookmark == null) {
            return;
        }
        FrameManager.getDefaultFrame().jumpTo(bookmark.getChr(), bookmark.getStart(), bookmark.getEnd());
        IGV.getInstance().getSession().getHistory().push(bookmark.getLocusString(),
                FrameManager.getDefaultFrame().getZoom());
    }

    private void toggleHighlightSelected() {
        Bookmark bookmark = getSelectedBookmark();
        if (bookmark == null) {
            return;
        }
        bookmark.setHighlighted(!bookmark.isHighlighted());
        if (bookmark.isHighlighted()) {
            RegionOfInterestPanel.setSelectedRegion(bookmark);
        } else if (RegionOfInterestPanel.getSelectedRegion() == bookmark) {
            RegionOfInterestPanel.setSelectedRegion(null);
        }
        IGV.getInstance().getSession().getBookmarksObservable().setChangedAndNotify();
        IGV.getInstance().repaint();
        reload();
    }

    private void deleteSelected() {
        Bookmark bookmark = getSelectedBookmark();
        if (bookmark == null) {
            return;
        }
        List<Bookmark> toRemove = new ArrayList<>();
        toRemove.add(bookmark);
        IGV.getInstance().removeBookmarks(toRemove);
        reload();
    }

    private static class BookmarkTableModel extends AbstractTableModel {

        private final String[] columns = {"Chromosome", "Start", "End", "Label", "Highlighted"};
        private List<Bookmark> bookmarks = new ArrayList<>();

        void reload(Collection<Bookmark> bookmarks) {
            this.bookmarks = new ArrayList<>(bookmarks);
            fireTableDataChanged();
        }

        Bookmark getBookmarkAt(int row) {
            return bookmarks.get(row);
        }

        @Override
        public int getRowCount() {
            return bookmarks.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Bookmark bookmark = bookmarks.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return bookmark.getChr();
                case 1:
                    return bookmark.getDisplayStart();
                case 2:
                    return bookmark.getDisplayEnd();
                case 3:
                    return bookmark.getLabel();
                case 4:
                    return bookmark.isHighlighted() ? "\u2713" : "";
                default:
                    return "";
            }
        }
    }
}
