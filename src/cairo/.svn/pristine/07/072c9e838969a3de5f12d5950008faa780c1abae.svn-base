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
package org.speechforge.cairo.server.tts;

import static org.speechforge.cairo.util.jmf.JMFUtil.CONTENT_DESCRIPTOR_RAW_RTP;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;

import javax.media.CannotRealizeException;
import javax.media.Controller;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.EndOfMediaEvent;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.Processor;
import javax.media.control.TrackControl;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.DataSource;
import javax.media.rtp.InvalidSessionAddressException;
import javax.media.rtp.RTPManager;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionAddress;

import org.apache.log4j.Logger;

/**
 * Handles playing of audio prompt files over an RTP output stream.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class RTPPlayer implements ControllerListener {

    private static Logger _logger = Logger.getLogger(RTPPlayer.class);

    private Object _lock = new Object();
    private Processor _processor;

    private RTPManager _rtpManager;

    public RTPPlayer(int localPort, InetAddress remoteAddress, int remotePort)
      throws InvalidSessionAddressException, IOException {

      SessionAddress localAddress = new SessionAddress(InetAddress.getLocalHost(), localPort);
      SessionAddress targetAddress = new SessionAddress(remoteAddress, remotePort);
      _rtpManager = RTPManager.newInstance();
      _rtpManager.initialize(localAddress);
      _rtpManager.addTarget(targetAddress);
    }

    public RTPPlayer(RTPManager rtpManager) {
        _rtpManager = rtpManager;
    }

    public void playPrompt(File promptFile) throws InterruptedException, IllegalStateException, IllegalArgumentException {
        if (promptFile != null && promptFile.exists()) {
            try {
                MediaLocator source = new MediaLocator(promptFile.toURL());
                playSource(source);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Specified prompt file does not exist: " + promptFile);
        }
    }

    public void playSource(MediaLocator source) throws InterruptedException, IllegalStateException {
        try {
            synchronized(this) {
                if (_processor != null) {
                    throw new IllegalStateException("Attempt to call playPrompt() when prompt already playing!");
                }
                DataSource dataSource = Manager.createDataSource(source);
                _processor = Manager.createProcessor(dataSource);
                _processor.addControllerListener(this);
            }

            configure();
            
            program();

            realize();

            play();

        } catch (InterruptedException e) {
            _logger.debug("playSource() interrupted, closing processor...");
            try {
                close();
            } catch (InterruptedException ie) {
                // TODO Auto-generated catch block
                _logger.debug(ie, ie);
            }
            throw e;
        } catch (Exception e) {
            _logger.warn("playSource(): encountered unexpected exception: ", e);
            try {
                close();
            } catch (InterruptedException ie) {
                // TODO Auto-generated catch block
                _logger.debug(ie, ie);
            }
            throw new RuntimeException("playSource() encountered unexpected exception", e);
        }

    }
    
    private void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    private void configure() throws IOException, InterruptedException {
        synchronized (_lock) {
            _processor.configure();
            while(_processor != null && _processor.getState() < Processor.Configured) {
                checkInterrupted();
                _lock.wait();
            }
            if (_processor == null) {
                throw new IOException("Processor closed unexpectedly!");
            }
        }
    }

    private void program() throws UnsupportedFormatException {

        TrackControl[] trackControls = _processor.getTrackControls();
        int tracks = (trackControls == null) ? -1 : trackControls.length;
        if (tracks != 1 || !trackControls[0].isEnabled()) {
            throw new UnsupportedFormatException("Cannot handle track count: " + tracks, null);
        }

        _processor.setContentDescriptor(CONTENT_DESCRIPTOR_RAW_RTP);

        Format[] supported = trackControls[0].getSupportedFormats();
        int formats = (supported == null) ? -1 : supported.length;
        if (formats < 1) {
            throw new UnsupportedFormatException(
                    "No supported formats found: " + formats, trackControls[0].getFormat());
        }
        trackControls[0].setFormat(supported[0]);
    }

    private void realize() throws CannotRealizeException, InterruptedException {
        synchronized (_lock) {
           _processor.realize();
           while(_processor != null && _processor.getState() < Controller.Realized) {
               checkInterrupted();
               _lock.wait();
           }
           if (_processor == null) {
               throw new CannotRealizeException("Processor closed unexpectedly!");
           }
        }
    }

    private void play() throws UnsupportedFormatException, IOException, InterruptedException {
        synchronized (_lock) {
    
            DataSource dataOutput = _processor.getDataOutput();
            SendStream sendStream = _rtpManager.createSendStream(dataOutput, 0);
            sendStream.start();
            //_logger.debug("init(): Waiting 5 seconds for send stream to start...");
            //Thread.sleep(5000);
            _processor.start();
    
            do {
                checkInterrupted();
                _lock.wait();

            } while(_processor != null);
            

            _logger.debug("play(): completed successfully.");
        }
    }
    
    private void close() throws InterruptedException {
        synchronized (_lock) {
            if (_processor != null && _processor.getState() > Processor.Configured) {
                _processor.close();
                do {
                    try {
                        _lock.wait();
                    } catch (InterruptedException e) {
                        _processor.removeControllerListener(this);
                        _processor = null;
                        throw e;
                    }
                } while(_processor != null);
            }
        }
    }

    /* (non-Javadoc)
     * @see javax.media.ControllerListener#controllerUpdate(javax.media.ControllerEvent)
     */
    public void controllerUpdate(ControllerEvent event) {
        synchronized (_lock) {
            if (_logger.isDebugEnabled()) {
                _logger.debug("ControllerEvent received: " + event);
            }
    
            if (event instanceof EndOfMediaEvent) {
                event.getSourceController().close();
            } else if (event instanceof ControllerClosedEvent) {
                event.getSourceController().removeControllerListener(this);
                _processor = null;
            }

            _lock.notifyAll();
        }
    }

}
