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
package org.speechforge.cairo.server.recorder;

import org.speechforge.cairo.exception.UnsupportedHeaderException;
import org.speechforge.cairo.server.MrcpGenericChannel;
import org.speechforge.cairo.server.rtp.RTPStreamReplicator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.request.RecordRequest;
import org.mrcp4j.message.request.StartInputTimersRequest;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.server.MrcpServerSocket;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.RecorderRequestHandler;

/**
 * Handles MRCPv2 recorder requests by delegating to a dedicated {@link org.speechforge.cairo.server.recorder.RTPRecorderChannel}.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class MrcpRecorderChannel extends MrcpGenericChannel implements RecorderRequestHandler {

    private static Logger _logger = Logger.getLogger(MrcpRecorderChannel.class);

    private RTPRecorderChannel _recorderChannel;
    private boolean _recording = false;

    public MrcpRecorderChannel(RTPRecorderChannel recorderChannel) {
        _recorderChannel = recorderChannel;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#record(org.mrcp4j.message.request.RecordRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse record(RecordRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        if (_recording) {
            statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
        } else {
            try {
                _recorderChannel.startRecording(true);
                statusCode = MrcpResponse.STATUS_SUCCESS;
                requestState = MrcpRequestState.IN_PROGRESS;
                _recording = true;
            } catch (IllegalStateException e){
                _logger.debug(e, e);
                statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
            } catch (IOException e){
                _logger.debug(e, e);
                statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
            }
        }
        // TODO: cache event acceptor if request is not complete
        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#stop(org.mrcp4j.message.request.StopRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse stop(StopRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        if (_recording) {
            try {
                _recorderChannel.stopRecording();
                statusCode = MrcpResponse.STATUS_SUCCESS;
                //requestState = MrcpRequestState.IN_PROGRESS;
                _recording = false;
            } catch (IllegalStateException e){
                statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
            }
        } else {
            statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
        }
        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#startInputTimers(org.mrcp4j.message.request.StartInputTimersRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse startInputTimers(StartInputTimersRequest request, MrcpSession session) {
        return session.createResponse(MrcpResponse.STATUS_SERVER_INTERNAL_ERROR, MrcpRequestState.COMPLETE);
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.MrcpGenericChannel#validateParam(org.mrcp4j.message.header.MrcpHeader)
     */
    @Override
    protected boolean validateParam(MrcpHeader header) throws UnsupportedHeaderException, IllegalValueException {
        // TODO: check if param is valid
        throw new UnsupportedHeaderException();
    }

    public static void main(String[] args) throws Exception {
        // We need three parameters to receive and record RTP transmissions
        // For example,
        //   java MrcpRecorderChannel "C:\\work\\cvs\\onomatopia\\cairo\\output\\prompts" 32416 42050

        String channelID = "32AECB23433801@recorder";
        
        if (args.length < 3) {
            printUsage();
        }

        int mrcpPort = -1;
        try {
            mrcpPort = Integer.parseInt(args[1]);
        } catch (Exception e){
            _logger.debug(e, e);
        }
        if (mrcpPort < 0) {
            printUsage();
        }

        int rtpPort = -1;
        try {
            rtpPort = Integer.parseInt(args[2]);
        } catch (Exception e){
            _logger.debug(e, e);
        }
        if (rtpPort < 0) {
            printUsage();
        }

        File dir = new File(args[0]);

        _logger.info("Starting up RTPStreamReplicator...");
        RTPStreamReplicator replicator = new RTPStreamReplicator(rtpPort);

        _logger.info("Starting up MrcpServerSocket...");
        MrcpServerSocket serverSocket = new MrcpServerSocket(mrcpPort);
        RTPRecorderChannel recorder = new RTPRecorderChannel(channelID, dir, replicator);
        serverSocket.openChannel(channelID, new MrcpRecorderChannel(recorder));

        _logger.info("MRCP recorder resource listening on port " + mrcpPort);

        _logger.info("Hit <enter> to shutdown...");
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        String cmd = consoleReader.readLine();
        Thread.sleep(90000);
        _logger.info("Shutting down...");
        replicator.shutdown();
    }

    static void printUsage() {
        System.err.println("Usage: MrcpRecorderChannel <recordDir> <mrcpPort> <rtpPort>");
        System.err.println("     <recordDir>: directory to place recordings of RTP transmissions");
        System.err.println("     <mrcpPort>: port to listen for MRCP messages");
        System.err.println("     <rtpPort>: port to listen for RTP transmissions");
        System.exit(0);
    }

}