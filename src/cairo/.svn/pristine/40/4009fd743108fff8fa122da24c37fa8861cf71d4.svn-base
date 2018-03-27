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
package org.speechforge.cairo.server.resource;

import org.speechforge.cairo.exception.ResourceUnavailableException;
import org.speechforge.cairo.util.sip.SipAgent;
import org.speechforge.cairo.util.sip.SdpMessage;
import org.speechforge.cairo.util.sip.SessionListener;
import org.speechforge.cairo.util.sip.SipSession;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sip.SipException;
import javax.sip.TimeoutEvent;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpResourceType;

/**
 * Implements a {@link org.speechforge.cairo.server.resource.ResourceServer} that can be utilized by MRCPv2
 * clients for establishing and managing connections to MRCPv2 resource implementations.
 * 
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ResourceServerImpl implements SessionListener {

    public static final String NAME = "ResourceServer";

    private static Logger _logger = Logger.getLogger(ResourceServerImpl.class);

    private long _channelID = System.currentTimeMillis();

    private ResourceRegistry _registryImpl;

    private SipAgent _ua;

    private String cairoSipAddress = "sip:cairo@speechforge.org";

    /**
     * TODOC
     * 
     * @param registryImpl
     * @throws RemoteException
     * @throws SipException
     */
    public ResourceServerImpl(ResourceRegistry registryImpl) throws RemoteException, SipException {
        super();
        _ua = new SipAgent(this, cairoSipAddress, "Cairo SIP Stack", 5060, "udp");

        _registryImpl = registryImpl;
    }

    /**
     * TODOC
     * 
     * @param port
     * @param registryImpl
     * @throws RemoteException
     */
    public ResourceServerImpl(int port, ResourceRegistryImpl registryImpl) throws RemoteException {
        _registryImpl = registryImpl;
    }

    private synchronized String getNextChannelID() { // TODO: convert from synchronized to atomic
        return Long.toHexString(_channelID++);
    }

    /**
     * Invite.
     * 
     * @param request
     *            the invite request
     * 
     * @return the invite response
     * 
     * @throws ResourceUnavailableException
     *             the resource unavailable exception
     * @throws RemoteException
     *             the remote exception
     * @throws SdpException
     *             the sdp exception
     */
    private SdpMessage invite(SdpMessage request) throws ResourceUnavailableException, RemoteException,
            SdpException {


        // determine if there receivers and/or transmitter channel requests in the invite
        // and preprocess the message so that it can be sent back as a response to the inviter
        // (i.e. set the channel and setup attributes).
        boolean receiver = false;
        boolean transmitter = false;
        try {
            for (MediaDescription md : request.getMrcpReceiverChannels()) {
                String channelID = getNextChannelID();
                String chanid = channelID + '@' + MrcpResourceType.SPEECHRECOG.toString();
                md.setAttribute("channel", chanid);
                md.setAttribute("setup", "passive");
                receiver = true;
            }
            for (MediaDescription md : request.getMrcpTransmitterChannels()) {
                String channelID = getNextChannelID();
                String chanid = channelID + '@' + MrcpResourceType.SPEECHSYNTH.toString();
                md.setAttribute("channel", chanid);
                md.setAttribute("setup", "passive");
                transmitter = true;
            }
        } catch (SdpException e) {
            _logger.debug(e, e);
            throw e;
        }

        // process the invitation (transmiiiter and/or receiver)
        if (transmitter) {
            Resource resource = _registryImpl.getResource(Resource.Type.TRANSMITTER);
            request = resource.invite(request);
        }

        if (receiver) {
            Resource resource = _registryImpl.getResource(Resource.Type.RECEIVER);
            request = resource.invite(request);
        } // TODO: catch exception and release transmitter resources

        // post process the message
        // - remove the resource attribute
        // TODO: change the host adresss
        for (MediaDescription md : request.getMrcpChannels()) {
            md.removeAttribute("resource");
        }
        // message.getSessionDescription().getConnection().setAddress(host);

        return request;
    }

    public SdpMessage processByeRequest(SdpMessage request, SipSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    public SdpMessage processInviteRequest(SdpMessage request, SipSession session) throws SdpException,
            ResourceUnavailableException, RemoteException {
        SdpMessage m = invite(request);
        return m;
    }

    public SdpMessage processInviteResponse(SdpMessage response, SipSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * TODOC
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        ResourceRegistryImpl rr = new ResourceRegistryImpl();
        ResourceServerImpl rs = new ResourceServerImpl(rr);

        Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        registry.rebind(ResourceRegistry.NAME, rr);
        // registry.rebind(ResourceServer.NAME, rs);

        _logger.info("Server and registry bound and waiting...");

    }

    public void processTimeout(TimeoutEvent event) {
        // TODO Auto-generated method stub
        
    }

}
