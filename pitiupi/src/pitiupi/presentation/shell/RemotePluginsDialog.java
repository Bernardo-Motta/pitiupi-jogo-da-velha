package pitiupi.presentation.shell;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import pitiupi.errors.PitiupiException;
import pitiupi.plugins.store.InstalledPluginRef;
import pitiupi.presentation.shell.ui.ShellUi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import pitiupi.plugins.remote.*;
import pitiupi.plugins.store.PluginStore;
import pitiupi.platform.PitiupiHome;
import pitiupi.errors.*;

public final class RemotePluginsDialog extends JDialog {

    private static final String CFG_URL = "plugins.serverUrl";

    private final PluginRepositoryClient client = new PluginRepositoryClient();
    private final PluginStore store = new PluginStore();
    private final JTextField urlField = new JTextField(28);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Nome", "Versão", "Autor", "Descrição"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel status = ShellUi.mutedLabel(" ");
    private List<RemotePluginEntry> entries = List.of();
    private URI currentBase;

    public RemotePluginsDialog(Frame owner) {
        super(owner, "Plugins online", true);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        try {
            urlField.setText(PitiupiHome.get().config().getString(CFG_URL, ""));
        } catch (IOException ignored) { }

        JButton refresh = new JButton("Atualizar lista");
        refresh.addActionListener(e -> refresh());
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Servidor:"), BorderLayout.WEST);
        top.add(urlField, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        ShellUi.borderlessScroll(scroll);
        scroll.setPreferredSize(new Dimension(640, 260));
        add(scroll, BorderLayout.CENTER);

        JButton install = new JButton("Instalar");
        install.addActionListener(e -> installSelected());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(install, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void refresh() {
        final URI base;
        try {
            base = PluginRepositoryClient.normalize(urlField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
            return;
        }
        try {
            PitiupiHome.get().config().setString(CFG_URL, urlField.getText().trim());
        } catch (IOException ignored) { }
        status.setText("Consultando…");
        new SwingWorker<List<RemotePluginEntry>, Void>() {
            @Override protected List<RemotePluginEntry> doInBackground() throws Exception {
                return client.fetchList(base, PluginListFormat.FILE_NAME);
            }
            @Override protected void done() {
                try {
                    entries = get();
                    currentBase = base;
                    fill();
                    status.setText(entries.size() + " plugin(s) no servidor");
                } catch (Exception ex) {
                    status.setText(" ");
                    showError(ex);
                }
            }
        }.execute();
    }

    private void fill() {
        model.setRowCount(0);
        for (RemotePluginEntry e : entries) {
            model.addRow(new Object[]{e.name(), e.version(), e.author(), e.description()});
        }
    }

    private void installSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || currentBase == null) {
            return;
        }
        RemotePluginEntry e = entries.get(table.convertRowIndexToModel(row));
        int ok = JOptionPane.showConfirmDialog(this,
                "Instalar " + e.name() + " " + e.version() + " de " + currentBase + "?\n"
                        + "Um plugin roda com o mesmo poder do programa. Só instale de quem você confia.",
                "Confirmar instalação", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        final URI base = currentBase;
        status.setText("Baixando " + e.file() + "…");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Path tmp = client.download(base, e, PitiupiHome.get().cacheDirectory());
                try {
                    store.installFromFile(tmp, e.file(), false);
                } finally {
                    Files.deleteIfExists(tmp);
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    status.setText("Instalado: " + e.name());
                    ApplicationRestarter.promptRestartIfNeeded(RemotePluginsDialog.this);
                } catch (Exception ex) {
                    status.setText(" ");
                    showError(ex);
                }
            }
        }.execute();
    }

    private void showError(Exception ex) {
        Throwable t = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                ? ex.getCause() : ex;
        String msg = t instanceof PitiupiException pe ? pe.getUserMessage() : "Erro: " + t.getMessage();
        if (t.getCause() != null && t.getCause().getMessage() != null) {
            msg += "\n(" + t.getCause().getMessage() + ")";
        }
        JOptionPane.showMessageDialog(this, msg, "Plugins online", JOptionPane.WARNING_MESSAGE);
    }
}
