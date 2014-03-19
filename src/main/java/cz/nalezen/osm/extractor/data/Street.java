package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonIgnore;

public class Street {
	private String name;
	private PointWgs84 position;
	private ArrayList<Address> addresses = new ArrayList<>();
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public PointWgs84 getPosition() {
		return position;
	}
	
	public void setPosition(PointWgs84 position) {
		this.position = position;
	}
	
	public ArrayList<Address> getAddresses() {
		return addresses;
	}
	
	public void setAddresses(ArrayList<Address> addresses) {
		this.addresses = addresses;
	}

	@JsonIgnore
	public Address assureAddress(int streetNr, int conscriptionNr) {
		for (Address address : addresses) {
			if (streetNr==address.getStreetNumber() && conscriptionNr==address.getConscriptionNumber()) {
				return address;
			}
		}
		
		Address address = new Address();
		address.setConscriptionNumber(conscriptionNr);
		address.setStreetNumber(streetNr);
		
		addresses.add(address);
		
		return address;
	}
}
