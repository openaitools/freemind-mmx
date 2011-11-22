/*FreeMind - A Program for creating and viewing Mindmaps
 *Copyright (C) 2000-2011 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitri Polivaev and others.
 *
 *See COPYING for Details
 *
 *This program is free software; you can redistribute it and/or
 *modify it under the terms of the GNU General Public License
 *as published by the Free Software Foundation; either version 2
 *of the License, or (at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program; if not, write to the Free Software
 *Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package plugins.map;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.OsmMercator;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileController;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

import plugins.map.MapNodePositionHolder.MapNodePositionListener;
import freemind.controller.actions.generated.instance.PlaceNodeXmlAction;
import freemind.controller.actions.generated.instance.XmlAction;
import freemind.extensions.HookRegistration;
import freemind.modes.MindMap;
import freemind.modes.MindMapNode;
import freemind.modes.ModeController;
import freemind.modes.mindmapmode.MindMapController;
import freemind.modes.mindmapmode.actions.xml.ActionFactory;
import freemind.modes.mindmapmode.actions.xml.ActionPair;
import freemind.modes.mindmapmode.actions.xml.ActorXml;

public class Registration implements HookRegistration, ActorXml,
		TileLoaderListener {

	private static final String PLUGINS_MAP_NODE_POSITION = MapNodePositionHolder.class
			.getName();

	/*
	 * Collects MapNodePositionHolder. This is necessary to be able to display
	 * them all efficiently.
	 */
	private HashSet/* MapNodePositionHolder s */mMapNodePositionHolders = new HashSet();

	private HashSet mMapNodePositionListeners = new HashSet();

	private final MindMapController controller;

	private final MindMap mMap;

	private final java.util.logging.Logger logger;

	private TileSource mTileSource;

	private TileController mTileController;

	private MemoryTileCache mTileCache;

	public Registration(ModeController controller, MindMap map) {
		this.controller = (MindMapController) controller;
		mMap = map;
		logger = controller.getFrame().getLogger(this.getClass().getName());
		mTileSource = new OsmTileSource.Mapnik();
		mTileCache = new MemoryTileCache();
		mTileController = new TileController(mTileSource, mTileCache, this);
	}

	/**
	 * @param pPosition
	 * @param pTileSource
	 */
	public TileImage getImageForTooltip(Coordinate pPosition, int pZoom,
			String pTileSource) {
		TileSource tileSource = FreeMindMapController.changeTileSource(
				pTileSource, null);
		if (tileSource != null) {
			mTileSource = tileSource;
			mTileController.setTileSource(tileSource);
		}
		int tileSize = mTileSource.getTileSize();
		int exactx = OsmMercator.LonToX(pPosition.getLon(), pZoom);
		int exacty = OsmMercator.LatToY(pPosition.getLat(), pZoom);
		int x = exactx / tileSize;
		int y = exacty / tileSize;
		// determine other surrounding tiles that are close to the exact
		// point.
		int dx = exactx % tileSize;
		int dy = exacty % tileSize;
		// determine quadrant of cursor in tile:
		if (dx < tileSize / 2) {
			x -= 1;
			dx += tileSize;
		}
		if (dy < tileSize / 2) {
			y -= 1;
			dy += tileSize;
		}
		TileImage tileImage = new TileImage();
		tileImage.setTiles(2, x, y, pZoom, mTileController, logger, dx, dy);
		// wait for tiles:
		int timeout = 60;
		while (timeout-- > 0) {
			try {
				if (tileImage.isLoaded() || tileImage.hasErrors()) {
					break;
				}
				Thread.sleep(100);
			} catch (Exception e) {
				freemind.main.Resources.getInstance().logException(e);
			}
		}
		return tileImage;
	}

	public void deRegister() {
		controller.getActionFactory().deregisterActor(getDoActionClass());
	}

	public void register() {
		controller.getActionFactory().registerActor(this, getDoActionClass());
	}

	public void registerMapNode(MapNodePositionHolder pMapNodePositionHolder) {
		mMapNodePositionHolders.add(pMapNodePositionHolder);
		for (Iterator it = mMapNodePositionListeners.iterator(); it.hasNext();) {
			MapNodePositionListener listener = (MapNodePositionListener) it
					.next();
			try {
				listener.registerMapNode(pMapNodePositionHolder);
			} catch (Exception e) {
				freemind.main.Resources.getInstance().logException(e);
			}
		}
	}

	public Set getMapNodePositionHolders() {
		return Collections.unmodifiableSet(mMapNodePositionHolders);
	}

	public void deregisterMapNode(MapNodePositionHolder pMapNodePositionHolder) {
		mMapNodePositionHolders.remove(pMapNodePositionHolder);
		for (Iterator it = mMapNodePositionListeners.iterator(); it.hasNext();) {
			MapNodePositionListener listener = (MapNodePositionListener) it
					.next();
			try {
				listener.deregisterMapNode(pMapNodePositionHolder);
			} catch (Exception e) {
				freemind.main.Resources.getInstance().logException(e);
			}
		}
	}

	public void registerMapNodePositionListener(
			MapNodePositionListener pMapNodePositionListener) {
		mMapNodePositionListeners.add(pMapNodePositionListener);
	}

	public void deregisterMapNodePositionListener(
			MapNodePositionListener pMapNodePositionListener) {
		mMapNodePositionListeners.remove(pMapNodePositionListener);
	}

	/**
	 * Set map position. Is undoable.
	 * 
	 * @param pTileSource
	 * 
	 */
	public void changePosition(MapNodePositionHolder pHolder,
			Coordinate pPosition, Coordinate pMapCenter, int pZoom,
			String pTileSource) {
		MindMapNode node = pHolder.getNode();
		PlaceNodeXmlAction doAction = createPlaceNodeXmlActionAction(node,
				pPosition, pMapCenter, pZoom, pTileSource);
		PlaceNodeXmlAction undoAction = createPlaceNodeXmlActionAction(node,
				pHolder.getPosition(), pHolder.getMapCenter(),
				pHolder.getZoom(), pHolder.getTileSource());
		ActionFactory actionFactory = controller.getActionFactory();
		actionFactory.startTransaction(PLUGINS_MAP_NODE_POSITION);
		actionFactory.executeAction(new ActionPair(doAction, undoAction));
		actionFactory.endTransaction(PLUGINS_MAP_NODE_POSITION);
	}

	/**
	 * @param pNode
	 * @param pPosition
	 * @param pMapCenter
	 * @param pZoom
	 * @param pTileSource
	 * @return
	 */
	private PlaceNodeXmlAction createPlaceNodeXmlActionAction(
			MindMapNode pNode, Coordinate pPosition, Coordinate pMapCenter,
			int pZoom, String pTileSource) {
		logger.info("Setting position of node " + pNode);
		PlaceNodeXmlAction action = new PlaceNodeXmlAction();
		action.setNode(controller.getNodeID(pNode));
		action.setCursorLatitude(pPosition.getLat());
		action.setCursorLongitude(pPosition.getLon());
		action.setMapCenterLatitude(pMapCenter.getLat());
		action.setMapCenterLongitude(pMapCenter.getLon());
		action.setZoom(pZoom);
		action.setTileSource(pTileSource);
		return action;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.mindmapmode.actions.xml.ActorXml#act(freemind.controller
	 * .actions.generated.instance.XmlAction)
	 */
	public void act(XmlAction pAction) {
		if (pAction instanceof PlaceNodeXmlAction) {
			PlaceNodeXmlAction placeAction = (PlaceNodeXmlAction) pAction;
			MindMapNode node = controller.getNodeFromID(placeAction.getNode());
			MapNodePositionHolder hook = MapNodePositionHolder.getHook(node);
			if (hook != null) {
				hook.setMapCenter(new Coordinate(placeAction
						.getMapCenterLatitude(), placeAction
						.getMapCenterLongitude()));
				hook.setPosition(new Coordinate(
						placeAction.getCursorLatitude(), placeAction
								.getCursorLongitude()));
				hook.setZoom(placeAction.getZoom());
				hook.setTileSource(placeAction.getTileSource());
				// TODO: Only, if values really changed.
				controller.nodeChanged(node);
			} else {
				throw new IllegalArgumentException(
						"MapNodePositionHolder to node id "
								+ placeAction.getNode() + " not found.");
			}

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.modes.mindmapmode.actions.xml.ActorXml#getDoActionClass()
	 */
	public Class getDoActionClass() {
		return PlaceNodeXmlAction.class;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener#
	 * getTileCache()
	 */
	public TileCache getTileCache() {
		return mTileCache;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener#
	 * tileLoadingFinished(org.openstreetmap.gui.jmapviewer.Tile, boolean)
	 */
	public void tileLoadingFinished(Tile pTile, boolean pSuccess) {
		// TODO Auto-generated method stub

	}

}