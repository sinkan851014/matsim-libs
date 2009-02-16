/* *********************************************************************** *
 * project: org.matsim.*
 * MockDriverAgent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package playground.marcel.pt.mocks;

import org.matsim.network.Link;

import playground.marcel.pt.interfaces.DriverAgent;

public class MockDriverAgent implements DriverAgent {

	public Link chooseNextLink() {
		return null;
	}

	public void leaveLink(Link link) {
	}
	public void enterLink(Link link) {
	}

}
