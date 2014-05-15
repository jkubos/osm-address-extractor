package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;
import java.util.HashSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class City {
	
	public static class AuxiliaryData {
		public int minPostcodeMvcr;
		public int maxPostcodeMvcr;
	}
	
	private String name;
	private ArrayList<Address> addresses = new ArrayList<>();
	private ArrayList<Street> streets = new ArrayList<>();
	private HashSet<Integer> postcodes = new HashSet<>();
	private PointWgs84 position;
	
	private AuxiliaryData auxiliaryData;
	
	@JsonIgnore
	public AuxiliaryData assureAuxiliaryData() {
		if (auxiliaryData==null) {
			auxiliaryData = new AuxiliaryData();
		}
		
		return auxiliaryData;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public ArrayList<Address> getAddresses() {
		return addresses;
	}
	
	public void setAddresses(ArrayList<Address> addresses) {
		this.addresses = addresses;
	}
	
	public ArrayList<Street> getStreets() {
		return streets;
	}
	
	public void setStreets(ArrayList<Street> streets) {
		this.streets = streets;
	}
	
	public PointWgs84 getPosition() {
		return position;
	}
	
	public void setPosition(PointWgs84 position) {
		this.position = position;
	}

	public HashSet<Integer> getPostcodes() {
		return postcodes;
	}

	public void setPostcodes(HashSet<Integer> postcodes) {
		this.postcodes = postcodes;
	}

	@JsonIgnore
	public Street assureStreet(String streetName) {
		String cname = streetName.trim().toLowerCase();

		for (Street street : streets) {
			if (street.getName().equals(cname)) {
				return street;
			}
		}

		Street street = new Street();
		street.setName(cname);
		streets.add(street);
		
		return street;
	}
	
	@JsonIgnore
	public Address assureAddress(String mainNr, String auxNr) {
		for (Address address : addresses) {
			if (mainNr==address.getMainNumber() && auxNr==address.getAuxNumber()) {
				return address;
			}
		}
		
		Address address = new Address();
		address.setMainNumber(mainNr);
		address.setAuxNumber(auxNr);
		
		addresses.add(address);
		
		return address;
	}
}
