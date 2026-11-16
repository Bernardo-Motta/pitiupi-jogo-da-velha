package pitiupi.velha.GUI;

import pitiupi.velha.control.Partida;
import pitiupi.velha.control.VelhaController;
import pitiupi.velha.control.VelhaController.DesafioAberto;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Aba central do plugin: tabuleiro, desafios abertos e registro de eventos. */
public class VelhaPanel extends JPanel implements VelhaController.Listener {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color COR_X = new Color(0x1E, 0x5A, 0xA8);
    private static final Color COR_O = new Color(0xC0, 0x39, 0x2B);

    private final VelhaController controller;

    private final JLabel status = new JLabel(" ");
    private final JLabel placar = new JLabel(" ");
    private final JButton[] casas = new JButton[9];
    private final JButton desafiarSala = new JButton("Desafiar a sala");
    private final JButton cancelar = new JButton("Cancelar desafio");
    private final JButton desistir = new JButton("Desistir");
    private final DefaultListModel<DesafioAberto> modeloDesafios = new DefaultListModel<>();
    private final JList<DesafioAberto> listaDesafios = new JList<>(modeloDesafios);
    private final JButton aceitar = new JButton("Aceitar desafio");
    private final JTextArea registro = new JTextArea();

    public VelhaPanel(VelhaController controller) {
        super(new BorderLayout(10, 10));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(desafiarSala);
        topo.add(cancelar);
        topo.add(desistir);
        add(topo, BorderLayout.NORTH);

        JPanel grade = new JPanel(new GridLayout(3, 3, 4, 4));
        grade.setPreferredSize(new Dimension(330, 330));
        for (int i = 0; i < 9; i++) {
            final int pos = i;
            JButton b = new JButton(" ");
            b.setFont(b.getFont().deriveFont(Font.BOLD, 56f));
            b.setFocusPainted(false);
            b.addActionListener(e -> controller.jogar(pos));
            casas[i] = b;
            grade.add(b);
        }
        status.setFont(status.getFont().deriveFont(Font.BOLD, 15f));
        JPanel centro = new JPanel(new BorderLayout(5, 5));
        centro.add(placar, BorderLayout.NORTH);
        JPanel gradeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        gradeWrapper.add(grade);
        centro.add(gradeWrapper, BorderLayout.CENTER);
        centro.add(status, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        listaDesafios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel desafios = new JPanel(new BorderLayout(5, 5));
        desafios.setBorder(BorderFactory.createTitledBorder("Desafios abertos"));
        desafios.add(new JScrollPane(listaDesafios), BorderLayout.CENTER);
        desafios.add(aceitar, BorderLayout.SOUTH);

        registro.setEditable(false);
        registro.setLineWrap(true);
        registro.setWrapStyleWord(true);
        JScrollPane rolagem = new JScrollPane(registro);
        rolagem.setBorder(BorderFactory.createTitledBorder("Acontecimentos"));

        JPanel lateral = new JPanel(new GridLayout(2, 1, 5, 5));
        lateral.setPreferredSize(new Dimension(300, 0));
        lateral.add(desafios);
        lateral.add(rolagem);
        add(lateral, BorderLayout.EAST);

        desafiarSala.addActionListener(e -> controller.desafiar(null));
        cancelar.addActionListener(e -> controller.cancelarDesafio());
        desistir.addActionListener(e -> controller.desistir());
        aceitar.addActionListener(e -> {
            DesafioAberto d = listaDesafios.getSelectedValue();
            if (d == null && modeloDesafios.size() == 1) {
                d = modeloDesafios.get(0);
            }
            if (d != null) {
                controller.aceitar(d.partidaId());
            }
        });

        controller.setListener(this);
        atualizar();
        log("Clique em \"Desafiar a sala\" ou aceite um desafio aberto.");
    }

    @Override
    public void estadoMudou() {
        SwingUtilities.invokeLater(this::atualizar);
    }

    @Override
    public void log(String texto) {
        String linha = "[" + LocalTime.now().format(HORA) + "] " + texto + "\n";
        SwingUtilities.invokeLater(() -> {
            registro.append(linha);
            registro.setCaretPosition(registro.getDocument().getLength());
        });
    }

    private void atualizar() {
        Partida p = controller.partidaExibida();
        boolean minhaVez = controller.minhaVez();

        for (int i = 0; i < 9; i++) {
            char c = p == null ? '.' : p.casa(i);
            casas[i].setText(c == '.' ? " " : String.valueOf(c));
            casas[i].setForeground(c == 'X' ? COR_X : COR_O);
            casas[i].setEnabled(minhaVez && c == '.');
        }

        if (p == null) {
            placar.setText("Nenhuma partida ainda.");
        } else {
            placar.setText("Partida: " + p.getXNome() + " (X) × " + p.getONome() + " (O)");
        }

        if (controller.temDesafioPendente()) {
            status.setText("Desafio enviado. Esperando alguém aceitar…");
        } else if (controller.aguardandoAceite()) {
            status.setText("Aceite enviado. Esperando confirmação…");
        } else if (p == null) {
            status.setText("Desafie a sala para começar.");
        } else if (minhaVez) {
            status.setText("Sua vez! " + p.descricaoResultado());
        } else {
            status.setText(p.descricaoResultado());
        }

        boolean ocupado = controller.emPartida();
        desafiarSala.setEnabled(!ocupado && !controller.temDesafioPendente());
        cancelar.setEnabled(controller.temDesafioPendente());
        desistir.setEnabled(ocupado);

        DesafioAberto selecionado = listaDesafios.getSelectedValue();
        List<DesafioAberto> abertos = controller.getDesafiosAbertos();
        modeloDesafios.clear();
        for (DesafioAberto d : abertos) {
            modeloDesafios.addElement(d);
            if (selecionado != null && d.partidaId().equals(selecionado.partidaId())) {
                listaDesafios.setSelectedIndex(modeloDesafios.size() - 1);
            }
        }
        aceitar.setEnabled(!ocupado && !modeloDesafios.isEmpty());
    }
}
