/* *********************************************************************** *
 * project: org.matsim.*
 * OTFDoubleMVI.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.vis.otfvis;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import javax.swing.JFrame;

import org.matsim.vis.otfvis.data.OTFClientQuad;
import org.matsim.vis.otfvis.data.OTFConnectionManager;
import org.matsim.vis.otfvis.gui.OTFSlaveHost;
import org.matsim.vis.otfvis.handler.OTFLinkAgentsHandler;
import org.matsim.vis.otfvis.handler.OTFLinkLanesAgentsNoParkingHandler;
import org.matsim.vis.otfvis.opengl.drawer.OTFOGLDrawer;
import org.matsim.vis.otfvis.opengl.layer.OGLAgentPointLayer;
import org.matsim.vis.otfvis.opengl.layer.SimpleStaticNetLayer;
import org.matsim.vis.otfvis.opengl.layer.OGLAgentPointLayer.AgentPointDrawer;

/**
 * OTFDoubleMVI displays two movies in different areas of a split screen application.
 * 
 * @author dstrippgen
 *
 */
public class OTFDoubleMVI extends OTFClientFile {
	protected String filename2;
	
	public OTFDoubleMVI(String filename, String filename2) {
		super(filename, false);
		this.filename2 = filename2;
	}


	@Override
	public void run() {
		JFrame frame = prepareRun();

		OTFSlaveHost hostControl2;
		try {
			hostControl2 = new OTFSlaveHost("file:" + this.filename2);
			hostControl2.frame = frame;
			this.hostControl.addSlave(hostControl2);

			OTFConnectionManager connectR = this.connect.clone();
			connectR.remove(OTFLinkAgentsHandler.class);
			connectR.add(OTFLinkAgentsHandler.class,  SimpleStaticNetLayer.SimpleQuadDrawer.class);
			connectR.add(SimpleStaticNetLayer.SimpleQuadDrawer.class, SimpleStaticNetLayer.class);
			connectR.add(OTFLinkAgentsHandler.class,  AgentPointDrawer.class);
			connectR.add(OTFLinkLanesAgentsNoParkingHandler.class,  AgentPointDrawer.class);
			connectR.add(OGLAgentPointLayer.AgentPointDrawer.class, OGLAgentPointLayer.class);

			OTFClientQuad clientQ2 = hostControl2.createNewView(null, connectR);
			OTFOGLDrawer drawer2 = new OTFOGLDrawer(frame, clientQ2);
			drawer2.invalidate((int)hostControl.getTime());
			drawer2.replaceMouseHandler(((OTFOGLDrawer)rightComp).getMouseHandler());
			hostControl.addHandler("test", drawer2);
			this.pane.setLeftComponent(drawer2.getComponent());
			pane.setDividerLocation(0.5);
			pane.setResizeWeight(0.5);
			//do not call for slave hosts!
			
			hostControl.finishedInitialisition();
			
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

	public static void main( String[] args) {
		String filename = null;
		String filename2 = null;
		if (args.length == 2) {
			filename = args[0];
			filename2 = args[1];
		} 
		
		OTFDoubleMVI client = new OTFDoubleMVI(filename, filename2);
		client.run();
	}

}

