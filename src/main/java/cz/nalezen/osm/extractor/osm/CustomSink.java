package cz.nalezen.osm.extractor.osm;

import java.util.Map;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

public class CustomSink implements Sink {
	
	private GeoExtractor geoExtractor;
	
	private int count = 0;
	private int max = 0;
	
	private long last = System.currentTimeMillis();

	public CustomSink(GeoExtractor geoExtractor) {
		this.geoExtractor = geoExtractor;
	}

	@Override
	public void initialize(Map<String, Object> metaData) {
		max = count;
		count = 0;
	}

	@Override
	public void complete() {
	}

	@Override
	public void release() {
	}

	@Override
	public void process(EntityContainer entityContainer) {
		Entity entity = entityContainer.getEntity();
		
		geoExtractor.handle(entity);
		
		if (System.currentTimeMillis()-last>1000) {
			last = System.currentTimeMillis();
			
			if (max>0) {
				System.out.println(((count/(double)max)*100)+"% done");
			} else {
				System.out.println(count+" entities done");
			}
		}
		
		++count;	
	}
}
