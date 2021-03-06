/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;

/**
 * An implementation of AreaROI that makes use of Java AWT Shapes.
 * <p>
 * If available, this is a better choice than using AreaROI directly, due to the extra checking involved with AWT.
 * 
 * @author Pete Bankhead
 *
 */
class AWTAreaROI extends AreaROI implements TranslatableROI, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Path2D shape;
	
	// We potentially spend a lot of time drawing polygons & assessing whether or not to draw them...
	// By caching the bounds this can be speeded up
	transient private ClosedShapeStatistics stats = null;
	
	AWTAreaROI(Shape shape) {
		this(shape, null);
	}
	
	AWTAreaROI(Shape shape, ImagePlane plane) {
		super(getVertices(shape), plane);
		this.shape = new Path2D.Float(shape);
	}
	
	AWTAreaROI(AreaROI roi) {
		super(roi.vertices, roi.getImagePlane());
		shape = new Path2D.Float();
		for (Vertices vertices : vertices) {
			if (vertices.isEmpty())
				continue;
			shape.moveTo(vertices.getX(0), vertices.getY(0));
			for (int i = 1; i < vertices.size(); i++) {
				shape.lineTo(vertices.getX(i), vertices.getY(i));				
			}
			shape.closePath();			
		}
	}
	
	/**
	 * Get the number of vertices used to represent this area.
	 * There is some 'fuzziness' to the meaning of this, since
	 * curved regions will be flattened and the same complex areas may be represented in different ways - nevertheless
	 * it provides some measure of the 'complexity' of the area.
	 * @return
	 */
	@Override
	public int nVertices() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getNVertices();
	}
	
	
	
//	/**
//	 * For a while, ironically, PathAreaROIs didn't know their own areas...
//	 */
	@Override
	public double getArea() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}

	@Override
	public double getPerimeter() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getPerimeter();
	}

	@Override
	public Shape getShape() {
		return new Path2D.Float(shape);
	}
	
	@Override
	public String getRoiName() {
		return "Area (AWT)";
	}

	/**
	 * Get the x coordinate of the ROI centroid;
	 * <p>
	 * Warning: If the centroid computation was too difficult (i.e. the area is particularly elaborate),
	 * then the center of the bounding box will be used instead!  (However this should not be relied upon as it is liable to change in later versions)
	 */
	@Override
	public double getCentroidX() {
		if (stats == null)
			calculateShapeMeasurements();
		double centroidX = stats.getCentroidX();
		if (Double.isNaN(centroidX))
			return getBoundsX() + .5 * getBoundsWidth();
		else
			return centroidX;
	}

	/**
	 * Get the y coordinate of the ROI centroid;
	 * <p>
	 * Warning: If the centroid computation was too difficult (i.e. the area is particularly elaborate),
	 * then the center of the bounding box will be used instead!  (However this should not be relied upon as it is liable to change in later versions)
	 */
	@Override
	public double getCentroidY() {
		if (stats == null)
			calculateShapeMeasurements();
		double centroidY = stats.getCentroidY();
		if (Double.isNaN(centroidY))
			return getBoundsY() + .5 * getBoundsHeight();
		else
			return centroidY;
	}

	@Override
	public boolean contains(double x, double y) {
		return shape.contains(x, y);
	}

	@Override
	public ROI duplicate() {
		return new AWTAreaROI(shape, getImagePlane());
	}

	@Override
	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(shape);
	}

	
	@Override
	public TranslatableROI translate(double dx, double dy) {
		// Shift the bounds
		if (dx == 0 && dy == 0)
			return this;
		// Shift the region
		AffineTransform at = AffineTransform.getTranslateInstance(dx, dy);
		return new AWTAreaROI(new Path2D.Float(shape, at), getImagePlane());
	}

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(shape, pixelWidth, pixelHeight).getArea();
	}

	@Override
	public double getScaledPerimeter(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getPerimeter() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(shape, pixelWidth, pixelHeight).getPerimeter();
	}

	@Override
	public double getBoundsX() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsX();
	}


	@Override
	public double getBoundsY() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsY();
	}


	@Override
	public double getBoundsWidth() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsWidth();
	}


	@Override
	public double getBoundsHeight() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsHeight();
	}

	@Override
	public List<Point2> getPolygonPoints() {
		if (shape == null)
			return Collections.emptyList();
		return getLinearPathPoints(shape, shape.getPathIterator(null, 0.5));
	}
	
	
	private Object writeReplace() {
		AreaROI roi = new AreaROI(getVertices(shape), ImagePlane.getPlaneWithChannel(c, z, t));
		return roi;
	}
	
	
	static List<Point2> getLinearPathPoints(final Path2D path, final PathIterator iter) {
		List<Point2> points = new ArrayList<>();
		double[] seg = new double[6];
		while (!iter.isDone()) {
			switch(iter.currentSegment(seg)) {
			case PathIterator.SEG_MOVETO:
				// Fall through
			case PathIterator.SEG_LINETO:
				points.add(new Point2(seg[0], seg[1]));
				break;
			case PathIterator.SEG_CLOSE:
//				// Add first point again
//				if (!points.isEmpty())
//					points.add(points.get(0));
				break;
			default:
				throw new RuntimeException("Invalid polygon " + path + " - only line connections are allowed");
			};
			iter.next();
		}
		return points;
	}
	
	
	
	static List<Vertices> getVertices(final Shape shape) {
		Path2D path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
		PathIterator iter = path.getPathIterator(null, 0.5);
		List<Vertices> verticesList = new ArrayList<>();
		MutableVertices vertices = null;
		double[] seg = new double[6];
		while (!iter.isDone()) {
			switch(iter.currentSegment(seg)) {
			case PathIterator.SEG_MOVETO:
				vertices = new DefaultMutableVertices(new DefaultVertices());
				// Fall through
			case PathIterator.SEG_LINETO:
				vertices.add(seg[0], seg[1]);
				break;
			case PathIterator.SEG_CLOSE:
//				// Add first point again
				vertices.close();
				verticesList.add(vertices.getVertices());
				break;
			default:
				throw new RuntimeException("Invalid polygon " + path + " - only line connections are allowed");
			};
			iter.next();
		}
		return verticesList;
	}
	
}
