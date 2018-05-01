/*
 * Cairo - Open source framework for control of speech media resources.
 *
 * Copyright (C) 2015 SpeechForge - http://www.speechforge.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contact: ngodfredsen@users.sourceforge.net
 *

changed by david@suendermann.com 2015-03-04 (propagating the rec result back to MRCP)
changed by david@suendermann.com 2015-03-03 (forwarding the most recent recording to Kaldi for recognition)

 */
package org.speechforge.cairo.server.recog.sphinx;

import edu.cmu.sphinx.frontend.endpoint.SpeechEndSignal;
import edu.cmu.sphinx.frontend.endpoint.SpeechStartSignal;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataStartSignal;

import org.speechforge.cairo.server.recog.sphinx.KaldiASR;
import org.speechforge.cairo.server.recog.sphinx.Options;
import org.speechforge.cairo.server.recog.sphinx.OutputProcess;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataMonitor;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataRecorder;
import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.server.recog.RecognitionResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Arrays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FloatData;
import edu.cmu.sphinx.jsgf.JSGFGrammar;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import java.io.InputStream;
import java.util.Properties;
import java.io.FileOutputStream;

import net.sourceforge.halef.HalefDbWriter;

/**
 * Provides a poolable recognition engine that takes raw audio data as input.
 *
 * @author ALexei V Ivanov {@literal <}<a
 * href="mailto:alexei_v_ivanov@ieee.org">alexei_v_ivanov@ieee.org</a>{@literal >}
 */
public class KaldiRecEngineWFST extends SphinxRecEngine {

    private boolean hotword = false;
    protected final JSGFGrammar _jsgfGrammar;
    KaldiASR asr = null;
    private volatile boolean _timeout = false;
    private SpeechDataMonitor speechDataMonitor;
    private volatile boolean isInSpeech = false;
    private ConfigurationManager cm;
    private int id;
    private int origin = -1;
    private volatile String kaldiIp;
    private volatile String asrModelName;
    private volatile boolean ignoreASR = false;
    private Properties prop;
    private Thread vad = null;
    private TimerThread timerThread = null;
    private KaldiRecThread recThread = null;

    public KaldiRecEngineWFST(ConfigurationManager cm, int id, int origin)
    throws IOException, PropertyException, InstantiationException {

        super(cm, id, origin);
        this.cm = cm;
        this.id = id;
        this.origin = origin;


        prop = new Properties();
        try {
            InputStream in = getClass().getClassLoader().getResourceAsStream("/config/kaldi.properties");
            prop.load(in);
            in.close();
        } catch (Exception e) {
            _logger.debug("Couldn't load kaldi.properties");
        }


        _logger.info("Creating Kaldi WFST Engine # " + id + " . Clone of # " + origin);

        _recognizer = (Recognizer) cm.lookup("recognizer" + origin);
        _recognizer.allocate();

        _jsgfGrammar = (JSGFGrammar) cm.lookup("grammar");

        speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor" + origin);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        }

        Object primaryInput = cm.lookup("primaryInput" + origin);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }

    public KaldiRecEngineWFST(ConfigurationManager cm, int id)
    throws IOException, PropertyException, InstantiationException {

        super(cm, id);
        this.cm = cm;
        this.id = id;

        prop = new Properties();
        try {
            InputStream in = getClass().getClassLoader().getResourceAsStream("kaldi.properties");
            prop.load(in);
            in.close();
        } catch (Exception e) {
            _logger.debug("Couldn't load kaldi.properties");
        }

        _logger.info("Creating Kaldi WFST Engine # " + id);

        _recognizer = (Recognizer) cm.lookup("recognizer" + id);
        _recognizer.allocate();

        _jsgfGrammar = (JSGFGrammar) cm.lookup("grammar");

        speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor" + id);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        }

        Object primaryInput = cm.lookup("primaryInput" + id);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }

    }

    @Override
    public void startRecogThread() {
        vad = new VADThread(cm, id, origin);
        vad.start();
        timerThread = new TimerThread();
        timerThread.start();
        recThread = new KaldiRecThread(cm, id, origin, "ws://" + kaldiIp);
        recThread.start();
    }

    @Override
    public synchronized void stopProcessing() {
        _logger.debug("KaldiRecEngineWFST is stopping processing...");
        try {
            timerThread.interrupt();
            recThread.interrupt();
            _logger.debug("KaldiRecEgnineWFST threads interrupted.");
        } catch (Exception e) {
            _logger.error(e);
        }

        super.stopProcessing();

    }

    @Override
    public synchronized void load(GrammarLocation grammarLocation) throws IOException {

        //_jsgfGrammar.loadJSGF("yesOrNo");

        _logger.debug("Loaded grammar file from baseUrl: " + grammarLocation.getBaseURL());
        _logger.debug("Grammar name was: " + grammarLocation.getGrammarName());

        asrModelName = grammarLocation.getGrammarName();
        kaldiIp = prop.getProperty(grammarLocation.getGrammarName());
        _logger.debug("Looking for |" + grammarLocation.getGrammarName() + "|");
        _logger.debug("kaldiIp is: " + kaldiIp);
        if (grammarLocation.getGrammarName().equals("ignore")) {
            ignoreASR = true;
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
            // LocalDate date = LocalDate.now();
            // String happend_at = date.format(formatter);
            // final String hevent = Json.createObjectBuilder()
            //                       .add("event", "VAD_RECOGNITION_ONLY")
            //                       .add("epoch", Long.toString(Instant.now().getEpochSecond()))
            //                       .add("happend_at", happend_at)
            //                       .add("server_ip", System.getenv("IP"))
            //                       .add("client_session_id", HalefDbWriter.getClientSessionId())
            //                       .add("cairo_call_id", HalefDbWriter.getCairoCallId())
            //                       .build().toString();
            // HalefDbWriter.logGanesha("cairo-event", hevent);
        }
        if (kaldiIp == null) {
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
            // LocalDate date = LocalDate.now();
            // String happend_at = date.format(formatter);
            // final String hevent = Json.createObjectBuilder()
            //                       .add("event", "FALLBACK_TO_DEFAULT_ASR_MODEL")
            //                       .add("epoch", Long.toString(Instant.now().getEpochSecond()))
            //                       .add("happend_at", happend_at)
            //                       .add("server_ip", System.getenv("IP"))
            //                       .add("client_session_id", HalefDbWriter.getClientSessionId())
            //                       .add("cairo_call_id", HalefDbWriter.getCairoCallId())
            //                       .build().toString();
            // HalefDbWriter.logGanesha("cairo-event", hevent);
            _logger.debug("Fallback to default Kaldi IP");
            kaldiIp = prop.getProperty("default");
        }
        if (kaldiIp == null) {
            _logger.debug("Could not load default Kaldi IP");
        }

    }



    public boolean isHotword() {
        return hotword;
    }

    /**
     * @param hotword the hotword to set
     */
    public void setHotword(boolean hotword) {
        this.hotword = hotword;
    }


    protected class TimerThread extends Thread {

        public TimerThread() {
        }

        public void run() {
            int counter = 0;

            while (counter < 60) {
                try {
                    TimerThread.sleep(100);
                } catch (InterruptedException e) {
                    _logger.debug("TimerThread interrupted");
                    return;
                } catch (Exception e) {
                    _logger.debug(e);
                }
                counter++;
                if (isInSpeech) {
                    break;
                }
            }
            if (isInSpeech) {
                counter = 0;
                while (counter < 300) {
                    try {
                        TimerThread.sleep(100);
                    } catch (InterruptedException e) {
                        _logger.debug("TimerThread interrupted");
                        return;
                    } catch (Exception e) {
                        _logger.debug(e);
                    }
                    counter++;
                    if (!isInSpeech) {
                        return;
                    }

                }
            }
            _timeout = true;
        }

    }

    protected class VADThread extends Thread {

        private SpeechDataRecorder _recorder;

        public VADThread(ConfigurationManager cm, int id, int origin) {
            this.setName("VADThread");
            if (origin != -1) {
                _recorder = (SpeechDataRecorder) cm.lookup("speechDataRecorder" + origin);
            } else {
                _recorder = (SpeechDataRecorder) cm.lookup("speechDataRecorder" + id);
            }

        }

        public void run() {
            boolean isFinished = false;
            final int BEFORESPEECH = 1;
            final int INSPEECH = 2;
            final int AFTERSPEECH = 3;
            final int DATAEND = 4;
            final int DATASTART = 5;

            int state = BEFORESPEECH;


            Data data = null;


            while (!isFinished) {
                data = _recorder.getData();
                if (data instanceof DataStartSignal) {
                    state = DATASTART;
                    System.out.println("-----------DATASTART");
                    _logger.debug("VAD STATE: DATASTART");
                }

                if (data instanceof SpeechStartSignal) {
                    state = INSPEECH;
                    System.out.println("----------INSPEECH");
                    _logger.debug("VAD STATE: INSPEECH");
                }

                if (data instanceof SpeechEndSignal) {
                    state = AFTERSPEECH;
                    System.out.println("----------AFTERSPEECH");
                    _logger.debug("VAD STATE: AFTERSPEECH");
                }

                if (data instanceof DataEndSignal) {
                    state = DATAEND;
                    System.out.println("----------DATAEND");
                    _logger.debug("VAD STATE: DATAEND");
                }
                if (state == DATAEND) {
                    isFinished = true;
                }
            }
        }

    }


    @ClientEndpoint(subprotocols = {"audio-streaming-protocol"})
    protected class KaldiRecThread extends Thread {
        private Basic endPoint;
        private volatile int inMessage = 0;
        private int outMessage = 0;
        private volatile String hypothesis = "";
        private Session session;
        private SpeechDataRecorder _recorder;

        public KaldiRecThread(ConfigurationManager cm, int id, int origin, String url) {
            this.setName("KaldiRecThread");
            if (origin != -1) {
                _recorder = (SpeechDataRecorder) cm.lookup("speechDataRecorder" + origin);
            } else {
                _recorder = (SpeechDataRecorder) cm.lookup("speechDataRecorder" + id);
            }
            if (!ignoreASR) {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                try {
                    session = container.connectToServer(KaldiRecThread.class, URI.create(url));
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        public void onMessage(String message) {
                            hypothesis = message;
                            inMessage++;
                            System.out.println("Received message: " + message);
                        }
                    });
                    endPoint = session.getBasicRemote();
                    _logger.debug("PATRICK: WebSocket connection established");
                } catch (DeploymentException e) {
                    _logger.debug("Kaldi could not be reached on IP address " + url);
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void run() {
            boolean wasInterrupted = false;
            if (!isInSpeech) _logger.debug("PATRICK: Waiting for speech");
            while (!isInSpeech && !wasInterrupted) {
                wasInterrupted = KaldiRecThread.interrupted();
                if (_timeout) {
                    _timeout = false;
                    _logger.debug("RecogThread got result: _timeout_ \n Utterance<sil>  \n");

                    String hypothesis = "";
                    String jsonReturn = Json.createObjectBuilder()
                                        .add("model_name", asrModelName)
                                        .add("model_version", "NO_VERSION")
                                        .add("first_best", hypothesis)
                                        .add("vad_state", "NO_SPEECH")
                                        .add("utterance", JsonValue.NULL)
                                        .build().toString();



                    // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
                    // LocalDate date = LocalDate.now();
                    // String happend_at = date.format(formatter);
                    // String hevent = Json.createObjectBuilder()
                    //                 .add("event", "RECOGNITION_FINISHED_NO_INPUT_TIMEOUT")
                    //                 .add("epoch", Long.toString(Instant.now().getEpochSecond()))
                    //                 .add("happend_at", happend_at)
                    //                 .add("server_ip", System.getenv("IP"))
                    //                 .add("client_session_id", HalefDbWriter.getClientSessionId())
                    //                 .add("cairo_call_id", HalefDbWriter.getCairoCallId())
                    //                 .build().toString();
                    // HalefDbWriter.logGanesha("cairo-event", hevent);




                    RecognitionResult result = new RecognitionResult(jsonReturn, null);
                    RecogListener recogListener = null;
                    synchronized (KaldiRecEngineWFST.this) {
                        recogListener = _recogListener;
                    }

                    if (recogListener == null) {
                        _logger.debug("KALDI: RecogThread.run(): _recogListener is null!");
                        recogListener.recognitionComplete(result); //AI temporary fix
                    } else {
                        _logger.debug("KALDI: Calling recogListener.recognitionComplete(result)...");
                        recogListener.recogFakeResult(result);
                    }

                    if (session != null) {
                        try {
                            session.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    _logger.debug("Recognition Timeout");
                    return;
                }
            }

            double[] values = null;
            int processed = 0;
            boolean isLast = false;
            while (isInSpeech && !ignoreASR && !wasInterrupted) {
                wasInterrupted = KaldiRecThread.interrupted();
                if (_recorder.available - processed > 0) {
                    ByteArrayOutputStream baos = _recorder.baos;
                    byte[] localCopy = baos.toByteArray();

                    byte[] newChunk = Arrays.copyOfRange(localCopy, processed, localCopy.length);
                    processed = localCopy.length;

                    int bitsPerSample = 16;
                    int sampleRate = 8000;
                    boolean isBigEndian = true;
                    boolean isSigned = true;


                    AudioFormat wavFormat = new AudioFormat(sampleRate, bitsPerSample, 1, isSigned, isBigEndian);
                    AudioFileFormat.Type outputType = getTargetType("wav");
                    ByteArrayInputStream bais = new ByteArrayInputStream(newChunk);
                    AudioInputStream ais = new AudioInputStream(bais, wavFormat, newChunk.length / wavFormat.getFrameSize());
                    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                    try {
                        AudioSystem.write(ais, outputType, baos2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    byte[] convertedChunkWithHeader = baos2.toByteArray();
                    byte[] audioChunk = Arrays.copyOfRange(convertedChunkWithHeader, 44, convertedChunkWithHeader.length);

                    isLast = false;
                    if (!isInSpeech) isLast = true;
                    try {
                        endPoint.sendBinary(ByteBuffer.wrap(audioChunk), isLast);
                        outMessage++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                if (_timeout) {
                    break;
                }
            }

            while (isInSpeech && ignoreASR && !wasInterrupted) {
                wasInterrupted = KaldiRecThread.interrupted();
                if (_timeout) {
                    break;
                }
            }

            if (!isLast && !ignoreASR && !wasInterrupted) {
                wasInterrupted = KaldiRecThread.interrupted();
                try {
                    byte[] end = new byte[2];
                    end[0] = 0;
                    end[1] = 0;
                    endPoint.sendBinary(ByteBuffer.wrap(end), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (inMessage < 1 && !ignoreASR && !wasInterrupted) _logger.debug("Patrick: Waiting for responses....");
            while (inMessage < 1 && !ignoreASR && !wasInterrupted) {
                wasInterrupted = KaldiRecThread.interrupted();
            }

            if (wasInterrupted) {
                asr = null;
                _timeout = false;
                isInSpeech = false;
                kaldiIp = null;
                asrModelName = null;
                ignoreASR = false;
                vad = null;
                timerThread = null;
                recThread = null;
                if (session != null) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                _logger.debug("Recognition cancelled");
                return;
            }

            if (ignoreASR) {
                hypothesis = "";
                ignoreASR = false;
            }
            if (_timeout) {
                _timeout = false;
                hypothesis = "_timeoutlongspeech_";
                isInSpeech = false;
            }
            hypothesis = hypothesis.replaceAll("<|>", "_");
            hypothesis = hypothesis.replaceAll("\\{|\\}", "");
            hypothesis = hypothesis.trim();



            /* Gathering log output */
            String finalHyp = hypothesis;
            String version = "NO_VERSION";
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, 10);
            final String DATE_FORMAT_NOW = "yyyyMMddHHmmssSSSS";
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
            String s = sdf.format(cal.getTime());
            String recordingFilename = s + ".wav";
            ByteArrayOutputStream baos = _recorder.baos;
            byte[] abAudioData = baos.toByteArray();
            int utteranceBytes = abAudioData.length;
            // 8000 samples per second * 2 bytes per sample
            float utterance_duration = utteranceBytes / (float)16000;
            utterance_duration = (float)(Math.round(utterance_duration * Math.pow(10, 2)) / Math.pow(10, 2));
            JsonObject utterance = Json.createObjectBuilder().add("filename", recordingFilename).add("duration", utterance_duration).build();
            String jsonReturn = Json.createObjectBuilder()
                                .add("model_name", asrModelName)
                                .add("model_version", "NO_VERSION")
                                .add("first_best", hypothesis.equals("_timeoutlongspeech_") ? "" : hypothesis)
                                .add("vad_state", hypothesis.equals("_timeoutlongspeech_") ? "LONG_SPEECH" : "NORMAL")
                                .add("utterance", utterance)
                                .build().toString();

            // String event = hypothesis.equals("_timeoutlongspeech_") ? "RECOGNITION_FINISHED_LONG_SPEECH_TIMEOUT" : "RECOGNITION_FINISHED_SUCCESS";
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
            // LocalDate date = LocalDate.now();
            // String happend_at = date.format(formatter);
            // String hevent = Json.createObjectBuilder()
            //                       .add("event", event)
            //                       .add("epoch", Long.toString(Instant.now().getEpochSecond()))
            //                       .add("happend_at", happend_at)
            //                       .add("server_ip", System.getenv("IP"))
            //                       .add("client_session_id", HalefDbWriter.getClientSessionId())
            //                       .add("cairo_call_id", HalefDbWriter.getCairoCallId())
            //                       .build().toString();
            // HalefDbWriter.logGanesha("cairo-event", hevent);

            wasInterrupted = KaldiRecThread.interrupted();
            if (wasInterrupted) {
                asr = null;
                _timeout = false;
                isInSpeech = false;
                kaldiIp = null;
                asrModelName = null;
                ignoreASR = false;
                vad = null;
                timerThread = null;
                recThread = null;
                if (session != null) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                _logger.debug("Recognition cancelled");
                return;
            }

            wasInterrupted = KaldiRecThread.interrupted();
            if (wasInterrupted) {
                asr = null;
                _timeout = false;
                isInSpeech = false;
                kaldiIp = null;
                asrModelName = null;
                ignoreASR = false;
                vad = null;
                timerThread = null;
                recThread = null;
                if (session != null) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                _logger.debug("Recognition cancelled");
                return;
            }

            RecognitionResult result = new RecognitionResult(jsonReturn, null);
            RecogListener recogListener = null;
            synchronized (KaldiRecEngineWFST.this) {
                recogListener = _recogListener;
            }

            if (recogListener == null) {
                _logger.debug("KALDI: RecogThread.run(): _recogListener is null!");
                recogListener.recognitionComplete(result); //AI temporary fix
            } else {
                _logger.debug("KALDI: Calling recogListener.recognitionComplete(result)...");
                recogListener.recognitionComplete(result);
            }

            if (session != null) {
                try {
                    session.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            _logger.debug("PATRICK: Final hypothesis: " + hypothesis);
            _logger.debug("RecogThread got result: " + hypothesis + "\n Utterance<sil>  \n");


            /* Do the recording here instead of SpeechDataRecorder */

            String dumpFilePath = (System.getenv("CAIRO_RECORD_DIR") == null ? "/tmp/cairo-record" : System.getenv("CAIRO_RECORD_DIR") + "/");
            //audio format parameters
            int bitsPerSample = 16;
            int sampleRate = 8000;
            boolean isBigEndian = true;
            boolean isSigned = true;
            // Create an audio format object (java sound api)
            AudioFormat wavFormat = new AudioFormat(sampleRate, bitsPerSample, 1, isSigned, isBigEndian);
            AudioFileFormat.Type outputType = getTargetType("wav");


            String wavName = dumpFilePath + s + ".wav";
            _logger.debug("v3. created audio Format Object " + wavFormat.toString());
            _logger.debug("v3. filename:" + wavName);

            // Get all the data from the recorder

            ByteArrayInputStream bais = new ByteArrayInputStream(abAudioData);
            AudioInputStream ais = new AudioInputStream(bais, wavFormat, abAudioData.length / wavFormat.getFrameSize());
            File outWavFile = new File(wavName);

            if (AudioSystem.isFileTypeSupported(outputType, ais)) {
                try {
                    AudioSystem.write(ais, outputType, outWavFile);
                    // event = "UTTERANCE_RECORDED";
                    // date = LocalDate.now();
                    // happend_at = date.format(formatter);
                    // hevent = Json.createObjectBuilder()
                    //          .add("event", event)
                    //          .add("epoch", Long.toString(Instant.now().getEpochSecond()))
                    //          .add("happend_at", happend_at)
                    //          .add("server_ip", System.getenv("IP"))
                    //          .add("client_session_id", HalefDbWriter.getClientSessionId())
                    //          .add("cairo_call_id", HalefDbWriter.getCairoCallId())
                    //          .build().toString();
                    // HalefDbWriter.logGanesha("cairo-event", hevent);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("output type not supported...");
            }


            _logger.debug("PATRICK: Thread stopped");
        }

    }

    public void speechStarted() {
        _logger.debug("KALDI-REMOTE: *@* SPEECH STARTED *@*");
        _logger.debug("PATRICK: KALDI speechStarted() ..............................");
        isInSpeech = true;
        super.speechStarted();
    }


    public void speechEnded() {
        _logger.debug("KALDI-REMOTE: *@* SPEECH ENDED *@*");
        _logger.debug("PATRICK: KALDI speechEnded() ................................");
        isInSpeech = false;
        super.speechEnded();
    }




    private static AudioFileFormat.Type getTargetType(String extension) {
        AudioFileFormat.Type[] typesSupported = AudioSystem.getAudioFileTypes();

        for (AudioFileFormat.Type aTypesSupported : typesSupported) {
            if (aTypesSupported.getExtension().equals(extension)) {
                return aTypesSupported;
            }
        }
        return null;
    }

    public static DoubleData FloatData2DoubleData(FloatData data) {
        int numSamples = data.getValues().length;

        double[] doubleData = new double[numSamples];
        float[] values = data.getValues();
        for (int i = 0; i < values.length; i++) {
            doubleData[i] = values[i];
        }
        return new DoubleData(doubleData, data.getSampleRate(), data.getCollectTime(), data.getFirstSampleNumber());
    }

}


