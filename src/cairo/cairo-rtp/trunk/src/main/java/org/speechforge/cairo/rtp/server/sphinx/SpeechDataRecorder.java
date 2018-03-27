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
 *
 *
 * changed 2014-09-10 by david@suendermann.com (changed the format of the date time used in the wave racording filenames to include seconds and milliseconds to allow for more than one recording per minute)
 *
 */

package org.speechforge.cairo.rtp.server.sphinx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.speechforge.cairo.rtp.server.SpeechEventListener;

import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DataStartSignal;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FloatData;
import edu.cmu.sphinx.frontend.endpoint.SpeechEndSignal;
import edu.cmu.sphinx.frontend.endpoint.SpeechStartSignal;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.*;

import org.apache.log4j.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Monitors a stream of speech data being processed and broadcasts start-of-speech and end-of-speech events.
 *
 * @author Spencer Lord {@literal <}<a href="salord@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class SpeechDataRecorder extends BaseDataProcessor {

    private static Logger _logger = Logger.getLogger(SpeechDataRecorder.class);

    private SpeechEventListener _speechEventListener = null;

    public ByteArrayOutputStream baos;
    public DataOutputStream dos;
    public int available = 0;

    private boolean isInSpeech;
    private boolean wasInSpeech = false;
    private boolean captureUtts = true;

    public static String now() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 10);
        final String DATE_FORMAT_NOW = "yyyyMMddHHmmssSSSS";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        return sdf.format(cal.getTime());
    }

    /**
     * TODOC
     */
    public SpeechDataRecorder() {
        super();
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
            isInSpeech = true;
            _logger.debug("SpeechStartSignal encountered!");
        } else if (data instanceof SpeechEndSignal) {
            if (!wasInSpeech) {
                wasInSpeech = true;
            } else {
                isInSpeech = false;
            }

            _logger.debug("SpeechEndSignal encountered!");
        } else if (data instanceof DataStartSignal) {
            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);
            _logger.debug("DataStartSignal encountered!");
        } else if (data instanceof DataEndSignal) {
            wasInSpeech = false;
            _logger.debug("DataEndSignal encountered!");
        }

        if ((isInSpeech) && (!wasInSpeech) && (data instanceof DoubleData || data instanceof FloatData)) {
            DoubleData dd = data instanceof DoubleData ? (DoubleData) data : FloatData2DoubleData((FloatData) data);
            double[] values = dd.getValues();
            for (double value : values) {
                try {
                    dos.writeShort(new Short((short) value));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            available = baos.toByteArray().length;
        }
        return data;
    }

    /**
     * Converts DoubleData object to FloatDatas.
     *
     * @param data the data
     * @return the double data
     */
    public static DoubleData FloatData2DoubleData(FloatData data) {
        int numSamples = data.getValues().length;

        double[] doubleData = new double[numSamples];
        float[] values = data.getValues();
        for (int i = 0; i < values.length; i++) {
            doubleData[i] = values[i];
        }
        return new DoubleData(doubleData, data.getSampleRate(), data.getCollectTime(), data.getFirstSampleNumber());
    }


    /**
     * Writes the data to a file.  The data should correspond to the utterance (post endpointing)
     * so it is the same that gets fed to recoginizer.  File names is a sequences number
     * that gets incremented for each utterance.
     *
     * @param data the data
     */
    private void stopRecordingData(Data data) {
        // Logic moved to cairo-server KaldiRecEngineWFST
    }
}
