package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class District {
	private String name;
	private ArrayList<City> cities = new ArrayList<>();
	
	public District() {
		
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		if (name.length()<3) {
			throw new RuntimeException(String.format("Too short district name: '%s'", name));
		}
		
		this.name = name;
	}
	
	public ArrayList<City> getCities() {
		return cities;
	}
	
	public void setCities(ArrayList<City> cities) {
		this.cities = cities;
		
		cities.forEach(city->city.setDistrict(this));
	}

	@JsonIgnore
	public City assureCity(String cityName) {
		String cname = cityName.trim().toLowerCase();

		for (City city : cities) {
			if (city.getName().equals(cname)) {
				return city;
			}
		}

		City city = new City(this);
		city.setName(cname);
		cities.add(city);
		
		return city;
	}
	
	public City lookupCity(String cityName) {
		String cname = cityName.trim().toLowerCase();

		for (City city : cities) {
			if (city.getName().equals(cname)) {
				return city;
			}
		}
		
		return null;
	}	
}
