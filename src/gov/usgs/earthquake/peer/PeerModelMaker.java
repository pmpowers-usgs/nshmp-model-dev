package gov.usgs.earthquake.peer;

import gov.usgs.earthquake.model.AreaCreator;
import gov.usgs.earthquake.model.AreaCreator.SourceData;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.opensha.eq.model.SourceType;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class PeerModelMaker {

	static final String SET1_CASE1 = "Set1-Case1";
	static final String SET1_CASE2 = "Set1-Case2";
	static final String SET1_CASE3 = "Set1-Case3";
	static final String SET1_CASE4 = "Set1-Case4";
	static final String SET1_CASE5 = "Set1-Case5";
	static final String SET1_CASE6 = "Set1-Case6";
	static final String SET1_CASE7 = "Set1-Case7";
	static final String SET1_CASE8A = "Set1-Case8a";
	static final String SET1_CASE8B = "Set1-Case8b";
	static final String SET1_CASE8C = "Set1-Case8c";
	static final String SET1_CASE10 = "Set1-Case10";
	static final String SET1_CASE11 = "Set1-Case11";
	
	static final String FAULT1_SOURCE = "PEER: Fault 1";
	static final String FAULT2_SOURCE = "PEER: Fault 2";
	static final String AREA1_SOURCE = "PEER: Area 1";
	
	static final String MODEL_DIR = "models/PEER";
	static final String SOURCE_FILE = "test.xml";
	
	static void writeModels() {
		
	}
	
	static void writeAreaSourceTests() {
		Path set1case10 = Paths.get(MODEL_DIR, SET1_CASE10, SourceType.AREA.toString(), SOURCE_FILE);
		AreaCreator ac = new AreaCreator(SET1_CASE10, 1.0, PeerTests.AREA_DEPTH_STR);
//		SourceData sd = AreaCreator.createSource(
//			AREA1_SOURCE,
//			PeerTests.AREA_SOURCE_BORDER, mfdData, mechStr, mst, gridScaling, strike)
//		ac.addSource(SourceData);
//		
//		Path set1case10 = Paths.get(MODEL_DIR, "Set1-Case11", SourceType.AREA.toString(), SOURCE_FILE);
			
	}
	
}
