package cz.nalezen.osm.extractor.osm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.openstreetmap.osmosis.core.domain.v0_6.Entity;

public class EntitiesLookup {
	
	public interface EntityHandler {
		void handle(Entity entity);
	}
	
	private HashMap<Long, Entity> lookup = new HashMap<>();
	private HashSet<Long> lookupRequests = new HashSet<>();
	private ArrayList<EntityHandler> handlers = new ArrayList<>();
	
	public void reset() {
		lookup.clear();
		lookupRequests.clear();
	}
	
	public void requestLookup(long id) {
		lookupRequests.add(id);
	}
	
	public Entity lookup(long id) {
		return lookup.get(id);
	}
	
	public boolean havePendingRequests() {
		return !lookupRequests.isEmpty();
	}

	public void newRound() {
		for (Long lr : lookupRequests) {
			lookup.put(lr, null);
		}
		
		lookupRequests.clear();
	}

	public void addIfRequested(Entity entity) {
		if (lookup.containsKey(entity.getId()) && lookup.get(entity.getId())==null) {
			lookup.put(entity.getId(), entity);
			
			for (EntityHandler handler : handlers) {
				handler.handle(entity);
			}
		}
	}

	public void addHandler(EntityHandler entityHandler) {
		handlers.add(entityHandler);
	}
}
