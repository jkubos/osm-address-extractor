package cz.nalezen.osm.extractor.data;

import java.util.ArrayList;
import java.util.HashSet;

public class CzechRepublicAddresses {
	private ArrayList<District> districts = new ArrayList<>();
	private HashSet<String> missedOsmStreetNames = new HashSet<>();

	public ArrayList<District> getDistricts() {
		return districts;
	}

	public void setDistricts(ArrayList<District> districts) {
		this.districts = districts;
	}

	public District assureDistrict(String name) {
		String cname = name.trim().toLowerCase();

		for (District district : districts) {
			if (district.getName().equals(cname)) {
				return district;
			}
		}

		District district = new District();
		district.setName(cname);
		districts.add(district);
		
		return district;
	}

	public District lookupDistrict(String name) {
		String cname = name.toLowerCase().trim();
		
		for (District district : districts) {
			if (district.getName().equals(cname)) {
				return district;
			}
		}
		
		return null;
	}

	public HashSet<String> getMissedOsmStreetNames() {
		return missedOsmStreetNames;
	}

	public void setMissedOsmStreetNames(HashSet<String> missedOsmStreetNames) {
		this.missedOsmStreetNames = missedOsmStreetNames;
	}
}
