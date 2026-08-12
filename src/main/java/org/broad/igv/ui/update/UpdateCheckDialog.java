package org.broad.igv.ui.update;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.util.BrowserLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * IGV-X: dialog shown when an update check completes.
 *
 * Three outcomes:
 *  - update available  -> offer Download (.dmg), Release Page, or Later
 *  - up to date        -> informational panel
 *  - check failed      -> error panel (network / feed problems)
 *
 * The dialog never performs the update itself; it opens the release page /
 * artifact in the user's browser (macOS open), which is the honest,
 * reversible path for a signed app on macOS.
 */
public class UpdateCheckDialog extends JDialog {

    private static final Logger log = LogManager.getLogger(UpdateCheckDialog.class);

    private static final String TITLE = "Check for Updates — IGV-X";

    /**
     * Show the result of a check.
     */
    public static void show(Window parent, UpdateChecker.UpdateInfo info) {
        UpdateCheckDialog dlg = new UpdateCheckDialog(parent, info);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    private UpdateCheckDialog(Window parent, UpdateChecker.UpdateInfo info) {
        super(parent, TITLE, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        main.setPreferredSize(new Dimension(520, 380));

        JLabel header = new JLabel();
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        JTextArea body = new JTextArea();
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        if (info == null || info.status == UpdateChecker.Status.FAILED) {
            header.setText("Could not check for updates");
            body.setText("IGV-X could not reach the update feed.\n\n" +
                    "This can happen when you are offline or the update server is\n" +
                    "temporarily unavailable.  You can check again later from the\n" +
                    "Help menu (Check for Updates).\n\n" +
                    "To get updates manually, visit:\n" +
                    "https://github.com/Micropeptide/IGV-X/releases");
            buttons.add(makeButton("Close", e -> dispose()));
        } else if (info.status == UpdateChecker.Status.UP_TO_DATE) {
            header.setText("IGV-X is up to date");
            body.setText("You are running the latest available IGV-X build.\n\n" +
                    "Thanks for using IGV-X!  Updates (with release notes) are\n" +
                    "published to the Micropeptide/IGV-X GitHub page.");
            buttons.add(makeButton("Close", e -> dispose()));
        } else {
            header.setText("IGV-X " + info.version + " is available");
            StringBuilder sb = new StringBuilder();
            sb.append("Your current version: ").append(org.broad.igv.Globals.VERSION).append("\n");
            sb.append("Latest version:      ").append(info.version).append("\n\n");
            if (info.notes != null && !info.notes.isEmpty()) {
                sb.append("What's new:\n").append(info.notes).append("\n\n");
            }
            sb.append("Choose Download to get the .dmg installer (opens in your\n" +
                    "browser).  You can keep using the current version.");
            body.setText(sb.toString());

            JButton download = makeButton("Download", e -> {
                String url = info.dmgUrl != null ? info.dmgUrl : info.zipUrl;
                if (url != null) {
                    try {
                        BrowserLauncher.openURL(url);
                    } catch (IOException ex) {
                        log.warn("Error opening update download", ex);
                    }
                }
                dispose();
            });
            JButton page = makeButton("Release Page", e -> {
                try {
                    BrowserLauncher.openURL(info.releaseUrl);
                } catch (IOException ex) {
                    log.warn("Error opening release page", ex);
                }
                dispose();
            });
            JButton later = makeButton("Later", e -> dispose());
            buttons.add(later);
            buttons.add(page);
            buttons.add(download);
            download.requestFocusInWindow();
        }

        main.add(header, BorderLayout.NORTH);
        main.add(scroll, BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        setContentPane(main);
        pack();
    }

    private static JButton makeButton(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }
}
