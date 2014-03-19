package cz.nalezen.osm.extractor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;

import cz.nalezen.osm.extractor.osm.GeoExtractor.AddressData;
import cz.nalezen.osm.extractor.osm.GeoExtractor.CityData;
import cz.nalezen.osm.extractor.osm.GeoExtractor.StreetData;

public class MapPrinter {
	private int width;
	private int height;
	
	private double sx;
	private double sy;
	
	private Envelope envelope;

	public MapPrinter(int width, int height, Envelope envelope) {
		this.width = width;
		this.height = height;
		
		this.envelope = envelope;
		
		sx = width/(envelope.getMaxX()-envelope.getMinX());
		sy = height/(envelope.getMaxY()-envelope.getMinY());
	}
	
	private Point transform(double px, double py) {
		int x = (int) ((px-envelope.getMinX())*sx);
		int y = (int) ((py-envelope.getMinY())*sy);
    	
		return new Point(x, height-y);
	}
	
	public void renderCities(String imgPath, ArrayList<CityData> data) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	    Graphics2D g = img.createGraphics();

	    g.setBackground(Color.white);
	    g.fillRect(0, 0, img.getWidth(), img.getHeight());
	    
	    long last = System.currentTimeMillis();
	    int i = 0;
	    
	    for (CityData cd : data) {
	    	
	    	int red = (int)(Math.random()*256);  
	    	int green = (int)(Math.random()*256);  
	    	int blue = (int)(Math.random()*256);  

	    	g.setColor(new Color(red,green,blue));
	    	
	    	renderStreets(g, cd);
	    	renderAddresses(g, cd.addresses);
	    	renderBoundary(g, cd);
	    	renderName(g, cd);
	    	
	    	if (System.currentTimeMillis()-last>1000) {
	    		last = System.currentTimeMillis();
	    		
	    		System.out.println(((i/(double)data.size())*100)+"% rendered");
	    	}
	    	
	    	++i;
	    }

	    try {
	    	ImageIO.write(img, "png", new File(imgPath));
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
	}

	private void renderBoundary(Graphics2D g, CityData cd) {
		if (cd.boundary==null) {
			return;
		}
		
		Polygon poly = new Polygon();
		
    	for (Coordinate p : cd.boundary.getCoordinates()) {
    		Point tp = transform(p.x, p.y);

    		poly.addPoint(tp.x, tp.y);
    	}
    	
    	g.drawPolygon(poly);
	}

	private void renderName(Graphics2D g, CityData cd) {
		if (cd.boundary!=null && cd.name!=null && !cd.boundary.getCentroid().isEmpty()) {
    		Point tp = transform(cd.boundary.getCentroid().getX(), cd.boundary.getCentroid().getY());

	    	g.drawString(cd.name, tp.x, tp.y);
    	}
	}

	private void renderStreets(Graphics2D g, CityData cd) {

		for (StreetData sd : cd.streets) {
			
//			com.vividsolutions.jts.geom.Point centroid = null;
			
			if (sd.path instanceof GeometryCollection) {
				GeometryCollection gc = (GeometryCollection) sd.path;

				for (int i=0;i<gc.getNumGeometries();++i) {
					renderLineGeometry(g, gc.getGeometryN(i));
					
//					centroid = gc.getGeometryN(i).getCentroid();
				}				
			} else {
				renderLineGeometry(g, sd.path);
				
//				centroid = sd.path.getCentroid();
			}
			
//			Point tp = transform(centroid.getX(), centroid.getY());
//	    	g.drawString(sd.name, tp.x, tp.y);
			
			renderAddresses(g, sd.addresses);
		}
		
		
	}

	private void renderLineGeometry(Graphics2D g, Geometry geom) {
		Point prev = null;
		
		for (Coordinate p : geom.getCoordinates()) {
    		Point tp = transform(p.x, p.y);
    		
	    	if (prev!=null) {
	    		g.drawLine(prev.x, prev.y, tp.x, tp.y);
	    	}
	    	
	    	prev = tp;
		}
	}

	private void renderAddresses(Graphics2D g, ArrayList<AddressData> addresses) {
		for (AddressData ad : addresses) {
			
			Point tp = transform(ad.position.getX(), ad.position.getY());
			
			g.fillOval(tp.x-1, tp.y-1, 2, 2);
		}
	}
}
