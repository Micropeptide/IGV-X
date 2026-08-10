package org.broad.igv.organize;

import org.broad.igv.organize.OrganizeRules.ContextRule;
import org.broad.igv.organize.OrganizeRules.GenotypeRule;
import org.broad.igv.ui.IGV;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * IGV-X: dialog to edit the organize-by-genotype rules and apply them to the
 * current session.  Rules persist to preferences and drive both the manual
 * "Organize Tracks" action and (optionally) auto-organization after loads.
 */
public class OrganizeTracksDialog extends JDialog {

    private final GenotypeTableModel genotypeModel;
    private final ContextTableModel contextModel;
    private final JCheckBox autoOrganizeBox;
    private final JTable genotypeTable;
    private final JTable contextTable;
    private final OrganizeRules rules;

    public OrganizeTracksDialog(Frame parent, OrganizeRules rules) {
        super(parent, "Organize Tracks by Genotype", true);
        this.rules = rules == null ? OrganizeRules.createDefaults() : rules;
        genotypeModel = new GenotypeTableModel(this.rules.getGenotypes());
        contextModel = new ContextTableModel(this.rules.getContexts());
        autoOrganizeBox = new JCheckBox("Auto-organize tracks when a session / batch load completes",
                OrganizeRules.isAutoOrganizeEnabled());

        genotypeTable = new JTable(genotypeModel);
        contextTable = new JTable(contextModel);

        JPanel genotypePanel = new JPanel(new BorderLayout(4, 4));
        genotypePanel.setBorder(BorderFactory.createTitledBorder("Genotype rules (first match wins)"));
        genotypePanel.add(new JScrollPane(genotypeTable), BorderLayout.CENTER);
        genotypePanel.add(buttonRow(
                e -> genotypeModel.addRow(),
                e -> genotypeModel.removeSelected(genotypeTable)), BorderLayout.SOUTH);

        JPanel contextPanel = new JPanel(new BorderLayout(4, 4));
        contextPanel.setBorder(BorderFactory.createTitledBorder("Methylation context rules (CG / CHG / CHH ...)"));
        contextPanel.add(new JScrollPane(contextTable), BorderLayout.CENTER);
        contextPanel.add(buttonRow(
                e -> contextModel.addRow(),
                e -> contextModel.removeSelected(contextTable)), BorderLayout.SOUTH);

        JPanel help = new JPanel();
        help.setLayout(new BoxLayout(help, BoxLayout.Y_AXIS));
        help.add(new JLabel("<html><body style='width:520px'>" +
                "<b>Name</b> = display name of the genotype / context.<br>" +
                "<b>Pattern</b> = regex matched anywhere in the track name; the first matching rule wins.<br>" +
                "<b>Background / Color</b> = hex color (#rrggbb) or a color name; click to edit.  " +
                "Context colors are consistent across genotypes.  Tracks matching no genotype rule are " +
                "auto-grouped from the name prefix before the context token.</body></html>"));
        help.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> apply());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(genotypePanel);
        center.add(contextPanel);
        content.add(autoOrganizeBox, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(help, BorderLayout.SOUTH);
        content.add(buttonPanel, BorderLayout.PAGE_END);

        getContentPane().add(content);
        pack();
        setSize(860, 520);
        setLocationRelativeTo(parent);
    }

    private JPanel buttonRow(java.awt.event.ActionListener add, java.awt.event.ActionListener remove) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(add);
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(remove);
        p.add(addBtn);
        p.add(removeBtn);
        return p;
    }

    private void apply() {
        rules.setGenotypes(genotypeModel.toRules());
        rules.setContexts(contextModel.toRules());
        rules.save();
        OrganizeRules.setAutoOrganizeEnabled(autoOrganizeBox.isSelected());
        TrackOrganizer.organize(IGV.getInstance(), rules);
        dispose();
    }

    static class GenotypeTableModel extends AbstractTableModel {
        private final String[] cols = {"Name", "Pattern", "Background"};
        private final List<GenotypeRule> rows;

        GenotypeTableModel(List<GenotypeRule> rows) {
            this.rows = new ArrayList<>(rows);
        }

        void addRow() {
            rows.add(new GenotypeRule("", "", new Color(0xEEF2F7)));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeSelected(JTable table) {
            int r = table.getSelectedRow();
            if (r >= 0) {
                rows.remove(r);
                fireTableRowsDeleted(r, r);
            }
        }

        List<GenotypeRule> toRules() {
            List<GenotypeRule> out = new ArrayList<>();
            for (GenotypeRule r : rows) {
                if (r.getName() != null && !r.getName().trim().isEmpty()) {
                    out.add(r);
                }
            }
            return out;
        }

        public int getRowCount() {
            return rows.size();
        }

        public int getColumnCount() {
            return cols.length;
        }

        public String getColumnName(int c) {
            return cols[c];
        }

        public Object getValueAt(int row, int col) {
            GenotypeRule r = rows.get(row);
            switch (col) {
                case 0: return r.getName();
                case 1: return r.getPattern();
                case 2: return r.getBackground() == null ? "" : OrganizeRules.colorToHex(r.getBackground());
            }
            return null;
        }

        public boolean isCellEditable(int row, int col) {
            return true;
        }

        public void setValueAt(Object v, int row, int col) {
            GenotypeRule r = rows.get(row);
            String s = v == null ? "" : v.toString().trim();
            switch (col) {
                case 0: r.setName(s); break;
                case 1: r.setPattern(s); break;
                case 2: r.setBackground(OrganizeRules.hexToColor(s)); break;
            }
            fireTableCellUpdated(row, col);
        }
    }

    static class ContextTableModel extends AbstractTableModel {
        private final String[] cols = {"Name", "Pattern", "Color"};
        private final List<ContextRule> rows;

        ContextTableModel(List<ContextRule> rows) {
            this.rows = new ArrayList<>(rows);
        }

        void addRow() {
            rows.add(new ContextRule("", "", new Color(0x888888)));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeSelected(JTable table) {
            int r = table.getSelectedRow();
            if (r >= 0) {
                rows.remove(r);
                fireTableRowsDeleted(r, r);
            }
        }

        List<ContextRule> toRules() {
            List<ContextRule> out = new ArrayList<>();
            for (ContextRule r : rows) {
                if (r.getName() != null && !r.getName().trim().isEmpty()) {
                    out.add(r);
                }
            }
            return out;
        }

        public int getRowCount() {
            return rows.size();
        }

        public int getColumnCount() {
            return cols.length;
        }

        public String getColumnName(int c) {
            return cols[c];
        }

        public Object getValueAt(int row, int col) {
            ContextRule r = rows.get(row);
            switch (col) {
                case 0: return r.getName();
                case 1: return r.getPattern();
                case 2: return r.getColor() == null ? "" : OrganizeRules.colorToHex(r.getColor());
            }
            return null;
        }

        public boolean isCellEditable(int row, int col) {
            return true;
        }

        public void setValueAt(Object v, int row, int col) {
            ContextRule r = rows.get(row);
            String s = v == null ? "" : v.toString().trim();
            switch (col) {
                case 0: r.setName(s); break;
                case 1: r.setPattern(s); break;
                case 2: r.setColor(OrganizeRules.hexToColor(s)); break;
            }
            fireTableCellUpdated(row, col);
        }
    }
}
