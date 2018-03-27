/*
 * Cairo - Open source framework for control of speech media resources.
 *
 * Copyright (C) 2005-2006 SpeechForge - http://www.speechforge.org
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
 */
package org.speechforge.cairo.rtp.server.sphinx;

import org.speechforge.cairo.rtp.server.SpeechEventListener;

import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DataStartSignal;
import edu.cmu.sphinx.frontend.endpoint.SpeechEndSignal;
import edu.cmu.sphinx.frontend.endpoint.SpeechStartSignal;

import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FloatData;

import javax.json.Json;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

import net.sourceforge.halef.HalefDbWriter;

/**
 * Monitors a stream of speech data being processed and broadcasts start-of-speech and end-of-speech events.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class SpeechDataMonitor extends BaseDataProcessor {

    private static Logger _logger = Logger.getLogger(SpeechDataMonitor.class);

    private ConcurrentLinkedQueue<Data> queue;
    private boolean isInSpeech = false;
    private boolean wasInSpeech = false;
    private SpeechEventListener _speechEventListener = null;

    /**
     * TODOC
     */
    public SpeechDataMonitor() {
        super();
        queue = new ConcurrentLinkedQueue<Data>();
    }

    public ConcurrentLinkedQueue<Data> getQueue() {
        return queue;
    }

    public void setSpeechEventListener(SpeechEventListener speechEventListener) {
        _speechEventListener = speechEventListener;
    }

    /* (non-Javadoc)
     * @see edu.cmu.sphinx.frontend.BaseDataProcessor#getData()
     */
    @Override
    public Data getData() throws DataProcessingException {
        Data data = getPredecessor().getData();
        if (data instanceof SpeechStartSignal) {
            if (!wasInSpeech) {
                broadcastSpeechStartSignal();
                isInSpeech = true;
            }
        } else if (data instanceof SpeechEndSignal) {
            if (!wasInSpeech) {
                broadcastSpeechEndSignal();
                isInSpeech = false;
                wasInSpeech = true;
            }

        } else if (data instanceof DataStartSignal) {
            wasInSpeech = false;
            _logger.debug("<<<<<<<<<<<<<<< DataStartSignal encountered!");
        } else if (data instanceof DataEndSignal) {
            wasInSpeech = false;
            _logger.debug(">>>>>>>>>>>>>>> DataEndSignal encountered!");
        }

        if ((isInSpeech) && (!wasInSpeech) && (data instanceof DoubleData || data instanceof FloatData)) {
            queue.add(data);
        }

        return data;
    }

    private void broadcastSpeechStartSignal() {
        if (_speechEventListener != null) {
            _speechEventListener.speechStarted();
        }
        _logger.debug("v2 *************** SpeechStartSignal encountered!");
        // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
        // LocalDate date = LocalDate.now();
        // String happend_at = date.format(formatter);
        // final String hevent = Json.createObjectBuilder()
        //                       .add("event", "VAD_SPEECH_START")
        //                       .add("epoch", Long.toString(Instant.now().getEpochSecond()))
        //                       .add("happend_at", happend_at)
        //                       .add("server_ip", System.getenv("IP"))
        //                       .add("client_session_id", HalefDbWriter.getClientSessionId())
        //                       .add("cairo_call_id", HalefDbWriter.getCairoCallId())
        //                       .build().toString();
        // HalefDbWriter.logGanesha("cairo-event", hevent);
    }

    private void broadcastSpeechEndSignal() {
        if (_speechEventListener != null) {
            _speechEventListener.speechEnded();
        }
        _logger.debug("v2 *************** SpeechEndSignal encountered!");
        // HALEF Event logging
        // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d H:m:s:S");
        // LocalDate date = LocalDate.now();
        // String happend_at = date.format(formatter);
        // final String hevent = Json.createObjectBuilder()
        //                       .add("event", "VAD_SPEECH_END")
        //                       .add("epoch", Long.toString(Instant.now().getEpochSecond()))
        //                       .add("happend_at", happend_at)
        //                       .add("server_ip", System.getenv("IP"))
        //                       .add("client_session_id", HalefDbWriter.getClientSessionId())
        //                       .add("cairo_call_id", HalefDbWriter.getCairoCallId())
        //                       .build().toString();

        // HalefDbWriter.logGanesha("cairo-event", hevent);
    }

}
