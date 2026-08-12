package org.broad.igv.ui.update;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.BrowserLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * IGV-X: "Request a Feature..." dialog. Lets the user compose a feature request and
 * opens the default mail client with a pre-filled message addressed to the IGV-X
 * developer (micropeptide@icloud.com). If no mail client is available the composed
 * text is copied to the clipboard and shown with the address so the user can send
 * it manually.
 */
public class FeatureRequestDialog extends JDialog {

    private static final Logger log = LogManager.getLogger(FeatureRequestDialog.class);
    private static final String FEATURE_EMAIL = "micropeptide@icloud.com";
    private static final String SUBJECT = "IGV-X feature request";

    private final JTextArea requestText;

    public FeatureRequestDialog(Frame owner) {
        super(owner, "Request a Feature", true);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel hint = new JLabel("<html>Describe the feature you would like added to IGV-X.<br>" +
                "It will be sent to the developer at <b>" + FEATURE_EMAIL + "</b>.</html>");
        panel.add(hint, BorderLayout.NORTH);

        requestText = new JTextArea(8, 50);
        requestText.setLineWrap(true);
        requestText.setWrapStyleWord(true);
        requestText.setBorder(BorderFactory.createEtchedBorder());
        JScrollPane scroll = new JScrollPane(requestText);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton send = new JButton("Send Request");
        send.addActionListener(new SendAction());
        buttons.add(cancel);
        buttons.add(send);
        panel.add(buttons, BorderLayout.SOUTH);

        getContentPane().add(panel);
        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(send);
        requestText.getAccessibleContext().setAccessibleName("Feature request text");
        hint.getAccessibleContext().setAccessibleName("Feature request instructions");
    }

    private class SendAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String body = requestText.getText();
            if (body == null || body.trim().isEmpty()) {
                MessageUtils.showMessage("Please describe the feature you would like before sending.");
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                try {
                    String mailto = "mailto:" + FEATURE_EMAIL +
                            "?subject=" + URLEncoder.encode(SUBJECT, StandardCharsets.UTF_8) +
                            "&body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
                    BrowserLauncher.openURL(mailto);
                    dispose();
                } catch (Exception ex) {
                    log.error("Error opening mail client", ex);
                    copyToClipboardAndShowAddress(body);
                }
            } else {
                copyToClipboardAndShowAddress(body);
            }
        }
    }

    private void copyToClipboardAndShowAddress(String body) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(body), null);
        } catch (Exception ex) {
            log.error("Error copying to clipboard", ex);
        }
        MessageUtils.showMessage("No mail client is available. Your request text has been copied " +
                "to the clipboard \u2014 please send it manually to " + FEATURE_EMAIL);
        dispose();
    }

    public static void show(Frame owner) {
        new FeatureRequestDialog(owner).setVisible(true);
    }
}
