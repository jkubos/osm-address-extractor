package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonIgnore;

public class District {
	private String name;
	private ArrayList<City> cities = new ArrayList<>();
	
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
	}
	
	@JsonIgnore
	public City assureCity(String cityName) {
		String cname = cityName.trim().toLowerCase();

		for (City city : cities) {
			if (city.getName().equals(cname)) {
				return city;
			}
		}

		City city = new City();
		city.setName(cname);
		cities.add(city);
		
		return city;
	}	
}
