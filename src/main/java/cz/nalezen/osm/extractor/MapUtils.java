package cz.nalezen.osm.extractor;

import java.text.Normalizer;

public class MapUtils {
	public static String stringToId(String val) {
		try {
			String regex = "[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+";
			
			String normalized = Normalizer.normalize(val, Normalizer.Form.NFKD);
			
			normalized = new String(normalized.replaceAll(regex, "").getBytes("ascii"), "ascii");
			
			return normalized.toLowerCase().replaceAll("\\s", "-");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}
}
