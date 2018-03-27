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
package org.speechforge.cairo.server.rtp;

import static org.speechforge.cairo.server.recog.sphinx.SourceAudioFormat.PREFERRED_MEDIA_FORMATS;
import static org.speechforge.cairo.util.jmf.JMFUtil.CONTENT_DESCRIPTOR_RAW;

import org.speechforge.cairo.util.jmf.ProcessorStarter;

import java.io.IOException;

import javax.media.Manager;
import javax.media.Processor;
import javax.media.ProcessorModel;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PushBufferDataSource;
import javax.media.rtp.Participant;
import javax.media.rtp.ReceiveStream;

import org.apache.log4j.Logger;

/**
 * Serves to replicate an incoming RTP audio stream so that it may be consumed by multiple
 * destinations at varying time intervals without starting or stopping the underlying data
 * source.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class RTPStreamReplicator extends RTPConsumer {

    private static Logger _logger = Logger.getLogger(RTPStreamReplicator.class);

    private PBDSReplicator _replicator;
    private Processor _processor;
    
    private int _port;

    public RTPStreamReplicator(int port) throws IOException {
        super(port);
        _port = port;
    }
    
    /**
     * TODOC
     * @return Returns the port.
     */
    public int getPort() {
        return _port;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.rtp.RTPConsumer#shutdown()
     */
    @Override
    public void shutdown() {
        super.shutdown();
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.rtp.RTPConsumer#streamReceived(javax.media.rtp.ReceiveStream, javax.media.protocol.PushBufferDataSource)
     */
    @Override
    public synchronized void streamReceived(ReceiveStream stream, PushBufferDataSource dataSource) {
        if (_replicator == null) {
            try {
                ProcessorModel pm = new ProcessorModel(
                        dataSource, PREFERRED_MEDIA_FORMATS, CONTENT_DESCRIPTOR_RAW);
                try {
                    _logger.debug("Creating realized processor...");
                    _processor = Manager.createRealizedProcessor(pm);
                    _processor.addControllerListener(new ProcessorStarter());
                } catch (IOException e){
                    throw e;
                } catch (javax.media.CannotRealizeException e){
                    throw (IOException) new IOException(e.getMessage()).initCause(e);
                } catch (javax.media.NoProcessorException e){
                    throw (IOException) new IOException(e.getMessage()).initCause(e);
                }

                _logger.debug("Processor realized.");

                PushBufferDataSource pbds = (PushBufferDataSource) _processor.getDataOutput();
                _replicator = new PBDSReplicator(pbds);
                _processor.start();
                this.notifyAll();
            } catch (IOException e) {
                _processor = null;
                _replicator = null;  // TODO: close properly
                _logger.warn(e, e);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.rtp.RTPConsumer#streamMapped(javax.media.rtp.ReceiveStream, javax.media.rtp.Participant)
     */
    @Override
    public void streamMapped(ReceiveStream stream, Participant participant) {
        // ignore
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.rtp.RTPConsumer#streamInactive(javax.media.rtp.ReceiveStream, boolean)
     */
    @Override
    public synchronized void streamInactive(ReceiveStream stream, boolean byeEvent) {
        //if (byeEvent) {
            _replicator = null; // TODO: close data source properly, make sure this triggers EndOfStreamEvent in replicated PBDS
            if (_processor != null) {
                if (_logger.isDebugEnabled()) {
                    _logger.debug("Closing RTP processor for SSRC=" + stream.getSSRC());
                }
                _processor.close();
                _processor = null;
            }
        //}
    }

    /**
     * TODOC
     * @param outputContentDescriptor A <code>ContentDescriptor</code> that describes the desired output content-type.
     * @param maxWait the maximum time to wait in milliseconds if the stream has not yet been received.
     * @return A new <code>Processor</code> that is in the <code>Realized</code> state.
     * @throws IOException if there are I/O problems creating the processor from the stream.
     * @throws IllegalStateException if the stream has not been received yet, and is not received within the maximum time to wait.
     */
    public synchronized Processor createRealizedProcessor(ContentDescriptor outputContentDescriptor, long maxWait)
      throws IOException, IllegalStateException {

        if (_replicator == null) {
            if (maxWait >= 0) {
                try {
                    this.wait(maxWait); //TODO: make sure timeout period has passed
                } catch (InterruptedException e) {
                    // TODO: throw this exception?
                    _logger.warn(e, e);
                }
            }
            if (_replicator == null) {
                throw new IllegalStateException("No RTP stream yet received!");
            }
        }


        ProcessorModel pm = new ProcessorModel(
            _replicator.replicate(), PREFERRED_MEDIA_FORMATS, outputContentDescriptor);
        Processor processor;
        try {
            _logger.debug("Creating realized processor...");
            processor = Manager.createRealizedProcessor(pm);
        } catch (IOException e){
            throw e;
        } catch (javax.media.CannotRealizeException e){
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        } catch (javax.media.NoProcessorException e){
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }

        _logger.debug("Processor realized.");

        return processor;

    }


}
