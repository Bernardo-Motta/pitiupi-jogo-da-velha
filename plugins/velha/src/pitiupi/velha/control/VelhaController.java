package pitiupi.velha.control;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;
import pitiupi.velha.net.VelhaMessage;
import pitiupi.velha.net.VelhaMessage.Tipo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Máquina de estados do protocolo (partida 1x1). Não usa Swing: a tela é avisada pelo
 * {@link Listener} e lê o estado pelos getters.
 *
 * <p>Os métodos são {@code synchronized} porque o núcleo entrega mensagens numa
 * thread de rede, os cliques chegam pela thread do Swing e o reenvio roda num
 * temporizador próprio.</p>
 */
public class VelhaController {

    /** Intervalo de reenvio (reanúncio do desafio, aceite e última jogada). */
    public static final long REENVIO_MS = 1500;
    /** Um desafio some da lista se não for reanunciado nesse tempo. */
    public static final long VALIDADE_DESAFIO_MS = 5000;
    /** Quantas vezes a última jogada é repetida depois do fim da partida. */
    private static final int REENVIOS_FINAIS = 3;

    public interface Listener {
        void estadoMudou();
        void log(String texto);
    }

    /** Um desafio aberto que chegou de outra pessoa. */
    public record DesafioAberto(String partidaId, String desafianteId, String desafianteNome,
                                boolean soParaMim, long vistoEm) {
        @Override
        public String toString() {
            return desafianteNome + (soParaMim ? " desafiou você" : " desafiou a sala");
        }
    }

    private final ConnectionManager connectionManager;
    private final String meuId = UUID.randomUUID().toString();
    private Listener listener;

    /** Desafio que eu criei e que ainda não tem adversário. */
    private VelhaMessage meuDesafio;
    /** Aceite que eu enviei e que ainda não teve resposta. */
    private VelhaMessage meuAceite;
    /** Partida em que eu jogo. */
    private Partida minhaPartida;
    /** Última mensagem minha da partida (INICIO ou JOGADA), reenviada até o outro responder. */
    private VelhaMessage minhaUltimaMensagem;
    private int reenviosFinaisRestantes;

    private final Map<String, DesafioAberto> desafiosAbertos = new LinkedHashMap<>();

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "velha-reenvio");
        t.setDaemon(true);
        return t;
    });

    public VelhaController(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        timer.scheduleAtFixedRate(this::tick, REENVIO_MS, REENVIO_MS, TimeUnit.MILLISECONDS);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private String meuNome() {
        String nome = connectionManager.getUserName();
        return nome == null || nome.isBlank() ? "anônimo" : nome;
    }

    // ------------------------------------------------------------------ ações da tela

    /** Cria um desafio. {@code alvoNome} null desafia a sala inteira. */
    public synchronized void desafiar(String alvoNome) {
        if (minhaPartidaAtiva()) {
            log("Termine a partida atual antes de desafiar de novo.");
            return;
        }
        minhaPartida = null;
        meuAceite = null;
        VelhaMessage m = new VelhaMessage(Tipo.DESAFIO, UUID.randomUUID().toString(), meuId, meuNome());
        m.setAlvoNome(alvoNome);
        meuDesafio = m;
        enviar(m);
        log(alvoNome == null ? "Você desafiou a sala. Esperando alguém aceitar…"
                : "Você desafiou " + alvoNome + ". Esperando a resposta…");
        avisar();
    }

    public synchronized void cancelarDesafio() {
        if (meuDesafio != null) {
            meuDesafio = null;
            log("Desafio cancelado. Ele some da lista dos outros em alguns segundos.");
            avisar();
        }
    }

    public synchronized void aceitar(String partidaId) {
        DesafioAberto d = desafiosAbertos.get(partidaId);
        if (d == null) {
            log("Esse desafio não está mais aberto.");
            return;
        }
        if (minhaPartidaAtiva()) {
            log("Você já está numa partida.");
            return;
        }
        meuDesafio = null;
        minhaPartida = null;
        meuAceite = new VelhaMessage(Tipo.ACEITE, partidaId, meuId, meuNome());
        enviar(meuAceite);
        log("Você aceitou o desafio de " + d.desafianteNome() + ". Aguardando confirmação…");
        avisar();
    }

    public synchronized void jogar(int posicao) {
        Partida p = minhaPartida;
        if (p == null || p.encerrada() || p.vez() != p.simboloDe(meuId)) {
            return;
        }
        if (!p.jogar(posicao)) {
            return;
        }
        VelhaMessage m = mensagemDaPartida(Tipo.JOGADA, p);
        m.setPosicao(posicao);
        minhaUltimaMensagem = m;
        enviar(m);
        if (p.encerrada()) {
            reenviosFinaisRestantes = REENVIOS_FINAIS;
            log("Fim de jogo: " + p.descricaoResultado());
        }
        avisar();
    }

    public synchronized void desistir() {
        Partida p = minhaPartida;
        if (p == null || p.encerrada()) {
            return;
        }
        p.registrarDesistencia(p.simboloDe(meuId));
        VelhaMessage m = mensagemDaPartida(Tipo.DESISTENCIA, p);
        minhaUltimaMensagem = m;
        reenviosFinaisRestantes = REENVIOS_FINAIS;
        enviar(m);
        log("Você desistiu. " + p.descricaoResultado());
        avisar();
    }

    // ------------------------------------------------------------------ recebimento

    public synchronized void receber(VelhaMessage m) {
        if (m == null || m.getTipo() == null || meuId.equals(m.getRemetenteId())) {
            return; // eco da minha própria mensagem multicast
        }
        switch (m.getTipo()) {
            case DESAFIO -> receberDesafio(m);
            case ACEITE -> receberAceite(m);
            case INICIO -> receberInicio(m);
            case JOGADA -> receberJogada(m);
            case DESISTENCIA -> receberDesistencia(m);
        }
        avisar();
    }

    private void receberDesafio(VelhaMessage m) {
        boolean paraSala = m.getAlvoNome() == null;
        boolean paraMim = Objects.equals(m.getAlvoNome(), meuNome());
        if (!paraSala && !paraMim) {
            return;
        }
        boolean novo = !desafiosAbertos.containsKey(m.getPartidaId());
        desafiosAbertos.put(m.getPartidaId(), new DesafioAberto(m.getPartidaId(), m.getRemetenteId(),
                m.getRemetenteNome(), paraMim, System.currentTimeMillis()));
        if (novo) {
            log(m.getRemetenteNome() + (paraMim ? " desafiou você!" : " desafiou a sala!"));
        }
    }

    private void receberAceite(VelhaMessage m) {
        if (meuDesafio != null && meuDesafio.getPartidaId().equals(m.getPartidaId())) {
            // primeiro aceite que chegou: esse é o adversário
            meuDesafio = null;
            minhaPartida = new Partida(m.getPartidaId(), meuId, meuNome(), m.getRemetenteId(), m.getRemetenteNome());
            minhaUltimaMensagem = mensagemDaPartida(Tipo.INICIO, minhaPartida);
            enviar(minhaUltimaMensagem);
            log("Partida contra " + m.getRemetenteNome() + " começou. Você é X e começa.");
        } else if (minhaPartida != null && minhaPartida.getId().equals(m.getPartidaId())
                && m.getRemetenteId().equals(minhaPartida.getOId()) && minhaPartida.getSeq() == 0) {
            // o adversário não recebeu o INICIO e repetiu o aceite
            enviar(mensagemDaPartida(Tipo.INICIO, minhaPartida));
        }
        // aceites atrasados de outras pessoas são ignorados: elas vão ver o INICIO
    }

    private void receberInicio(VelhaMessage m) {
        // o desafio foi preenchido: some da lista de todos
        desafiosAbertos.remove(m.getPartidaId());
        if (meuAceite == null || !meuAceite.getPartidaId().equals(m.getPartidaId())) {
            return; // partida de outras pessoas
        }
        meuAceite = null;
        if (meuId.equals(m.getJogadorOId())) {
            minhaPartida = Partida.from(m);
            minhaUltimaMensagem = null;
            log("Partida contra " + m.getJogadorXNome() + " começou. Você é O; " + m.getJogadorXNome() + " começa.");
        } else {
            log(m.getJogadorONome() + " aceitou antes de você. Esse desafio já tem adversário.");
        }
    }

    private void receberJogada(VelhaMessage m) {
        desafiosAbertos.remove(m.getPartidaId());
        Partida p = minhaPartidaDa(m);
        if (p == null) {
            return; // partida de outras pessoas: ignorada
        }
        char simboloRemetente = p.simboloDe(m.getRemetenteId());
        char quemDeveriaJogar = m.getSeq() % 2 == 1 ? 'X' : 'O';
        if (simboloRemetente == 0 || simboloRemetente != quemDeveriaJogar) {
            return; // só o adversário, na vez dele, pode mandar essa jogada
        }
        if (p.aceitarEstado(m.getSeq(), m.getTabuleiro())) {
            // a resposta do adversário confirma que minha jogada chegou
            minhaUltimaMensagem = null;
            if (p.encerrada()) {
                log("Fim de jogo: " + p.descricaoResultado());
            }
        }
    }

    private void receberDesistencia(VelhaMessage m) {
        Partida p = minhaPartidaDa(m);
        if (p == null) {
            return;
        }
        char quem = p.simboloDe(m.getRemetenteId());
        if (quem != 0 && !p.encerrada()) {
            p.registrarDesistencia(quem);
            minhaUltimaMensagem = null;
            log(m.getRemetenteNome() + " desistiu. " + p.descricaoResultado());
        }
    }

    /** A minha partida, se a mensagem for dela; null se for de outras pessoas. */
    private Partida minhaPartidaDa(VelhaMessage m) {
        if (minhaPartida != null && minhaPartida.getId().equals(m.getPartidaId())) {
            return minhaPartida;
        }
        if (meuAceite != null && meuAceite.getPartidaId().equals(m.getPartidaId())
                && meuId.equals(m.getJogadorOId())) {
            // perdi o INICIO, mas a jogada traz os jogadores
            meuAceite = null;
            minhaPartida = Partida.from(m);
            return minhaPartida;
        }
        return null;
    }

    // ------------------------------------------------------------------ reenvio

    private synchronized void tick() {
        try {
            if (meuDesafio != null) {
                enviar(meuDesafio);
            }
            if (meuAceite != null) {
                enviar(meuAceite);
            }
            if (minhaUltimaMensagem != null && minhaPartida != null) {
                if (!minhaPartida.encerrada()) {
                    enviar(minhaUltimaMensagem);
                } else if (reenviosFinaisRestantes > 0) {
                    reenviosFinaisRestantes--;
                    enviar(minhaUltimaMensagem);
                }
            }
            long agora = System.currentTimeMillis();
            boolean removeu = desafiosAbertos.values()
                    .removeIf(d -> agora - d.vistoEm() > VALIDADE_DESAFIO_MS);
            if (removeu) {
                avisar();
            }
        } catch (RuntimeException ex) {
            System.out.println("[velha] erro no reenvio: " + ex);
        }
    }

    // ------------------------------------------------------------------ apoio

    private VelhaMessage mensagemDaPartida(Tipo tipo, Partida p) {
        VelhaMessage m = new VelhaMessage(tipo, p.getId(), meuId, meuNome());
        m.setJogadores(p.getXId(), p.getXNome(), p.getOId(), p.getONome());
        m.setSeq(p.getSeq());
        m.setTabuleiro(p.tabuleiroTexto());
        return m;
    }

    private void enviar(VelhaMessage m) {
        try {
            MessageDispatcher.send(connectionManager, m, new SendTarget.BroadcastAll(), TransportMode.MULTICAST_UDP);
        } catch (Exception ex) {
            System.out.println("[velha] falha ao enviar " + m.getTipo() + ": " + ex);
        }
    }

    private boolean minhaPartidaAtiva() {
        return minhaPartida != null && !minhaPartida.encerrada();
    }

    private void avisar() {
        if (listener != null) {
            listener.estadoMudou();
        }
    }

    private void log(String texto) {
        if (listener != null) {
            listener.log(texto);
        }
    }

    // ------------------------------------------------------------------ leitura pela tela

    public synchronized String getMeuId() { return meuId; }
    public synchronized boolean temDesafioPendente() { return meuDesafio != null; }
    public synchronized boolean aguardandoAceite() { return meuAceite != null; }
    public synchronized boolean emPartida() { return minhaPartidaAtiva(); }

    /** A partida mostrada no tabuleiro (a atual ou a última que eu joguei). */
    public synchronized Partida partidaExibida() {
        return minhaPartida;
    }

    public synchronized boolean minhaVez() {
        return minhaPartidaAtiva() && minhaPartida.vez() == minhaPartida.simboloDe(meuId);
    }

    public synchronized List<DesafioAberto> getDesafiosAbertos() {
        return new ArrayList<>(desafiosAbertos.values());
    }

    public void encerrar() {
        timer.shutdownNow();
    }
}
