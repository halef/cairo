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

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpResourceType;

/**
 * Implements a {@link org.speechforge.cairo.server.resource.ResourceServer} that can be utilized by
 * MRCPv2 clients for establishing and managing connections to MRCPv2 resource implementations.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ResourceServerImpl extends UnicastRemoteObject implements ResourceServer {
    
    private static Logger _logger = Logger.getLogger(ResourceServerImpl.class);

    private long _channelID = System.currentTimeMillis(); 
    private ResourceRegistryImpl _registryImpl;

    /**
     * TODOC
     * @param registryImpl 
     * @throws RemoteException
     */
    public ResourceServerImpl(ResourceRegistryImpl registryImpl) throws RemoteException {
        super();
        _registryImpl = registryImpl;
    }

    /**
     * TODOC
     * @param port
     * @param registryImpl 
     * @throws RemoteException
     */
    public ResourceServerImpl(int port, ResourceRegistryImpl registryImpl) throws RemoteException {
        super(port);
        _registryImpl = registryImpl;
    }
    
    private synchronized String getNextChannelID() { // TODO: convert from synchronized to atomic
        return Long.toHexString(_channelID++);
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.resource.ResourceServer#invite(org.speechforge.cairo.server.resource.ResourceMessage)
     */
    public ResourceMessage invite(ResourceMessage request) throws ResourceUnavailableException, RemoteException {
        try {
            return invitePrivate(request);
        } catch (RemoteException e) {
            _logger.debug(e, e);
            throw e;
        } catch (ResourceUnavailableException e) {
            _logger.debug(e, e);
            throw e;
        }
    }

    private ResourceMessage invitePrivate(ResourceMessage request) throws ResourceUnavailableException, RemoteException {
        String channelID = getNextChannelID();

        boolean receiver = false;
        boolean transmitter = false;

        for (ResourceChannel channel : request.getChannels()) {
            MrcpResourceType resourceType = channel.getResourceType();
            channel.setChannelID(channelID + '@' + resourceType.toString());
            Resource.Type type = Resource.Type.fromMrcpType(resourceType);
            if (type.equals(Resource.Type.RECEIVER)) {
                receiver = true;
            } else {
                transmitter = true;
            }
        }

        if (transmitter) {
            Resource resource = _registryImpl.getResource(Resource.Type.TRANSMITTER);
            request = resource.invite(request);
        }

        if (receiver) {
            Resource resource = _registryImpl.getResource(Resource.Type.RECEIVER);
            request = resource.invite(request);
        } // TODO: catch exception and release transmitter resources

        return request;
    }

    /**
     * TODOC
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        ResourceRegistryImpl rr = new ResourceRegistryImpl();
        ResourceServerImpl rs = new ResourceServerImpl(rr);

        Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        registry.rebind(ResourceRegistry.NAME, rr);
        registry.rebind(ResourceServer.NAME, rs);

        _logger.info("Server and registry bound and waiting...");

    }

}
