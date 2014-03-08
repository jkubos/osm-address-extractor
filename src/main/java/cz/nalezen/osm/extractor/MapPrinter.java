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

import cz.nalezen.osm.extractor.GeoExtractor.CityData;
import cz.nalezen.osm.extractor.GeoExtractor.StreetData;

public class MapPrinter {
	private static final int IMG_WIDTH = 16000;
	private static final int IMG_HEIGHT = 12000;

	//CZ
	private static final double BX = 12.09;
	private static final double BY = 48.55;
	private static final double EX = 18.87;
	private static final double EY = 51.06;
	
	//Brno
//	private static final double BX = 16.33;
//	private static final double BY = 49.03;
//	private static final double EX = 17.10;
//	private static final double EY = 49.32;
	
	private static final double SX = IMG_WIDTH/(EX-BX);
	private static final double SY = IMG_HEIGHT/(EY-BY);
	
	private static Point transform(double px, double py) {
		int x = (int) ((px-BX)*SX);
		int y = (int) ((py-BY)*SY);
    	
		return new Point(x, IMG_HEIGHT-y);
	}
	
	public static void renderCities(String imgPath, ArrayList<CityData> data) {
		BufferedImage img = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
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

	private static void renderBoundary(Graphics2D g, CityData cd) {
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

	private static void renderName(Graphics2D g, CityData cd) {
		if (cd.boundary!=null && cd.name!=null && !cd.boundary.getCentroid().isEmpty()) {
    		Point tp = transform(cd.boundary.getCentroid().getX(), cd.boundary.getCentroid().getY());

	    	g.drawString(cd.name, tp.x, tp.y);
    	}
	}

	private static void renderStreets(Graphics2D g, CityData cd) {

		for (StreetData sd : cd.streets) {
			
			Point prev = null;
			
			for (Coordinate p : sd.path.getCoordinates()) {
	    		Point tp = transform(p.x, p.y);
	    		
		    	if (prev!=null) {
		    		g.drawLine(prev.x, prev.y, tp.x, tp.y);
		    	}
		    	
		    	prev = tp;
			}
			
//			Point tp = transform(sd.path.getCentroid().getX(), sd.path.getCentroid().getY());
//
//	    	g.drawString(sd.name, tp.x, tp.y);
		}
		
		
	}
}
