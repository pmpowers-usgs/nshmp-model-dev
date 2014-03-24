package scratch.peter.erfxml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.DataUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class UC3_Export {

	private static final String COMP_SOL_PATH =
			"tmp/UC33/src/tree/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip";
	private static final String BR_AVG_SOL_PATH =
			"tmp/UC33/src/bravg/FM-DM-MS/UC33brAvg_FM31_ABM_ELLB.zip";
	
	private static final String TEST_BRANCH = "FM3_1_ABM_EllB_DsrTap_CharConst_M5Rate6.5_MMaxOff7.3_NoFix_SpatSeisU2";
	
	private static final String TEST_GRID_XML_IN = "tmp/tmpsol/UC33brAvg_FM31_ABM_ELLB/grid_sources.xml";
	private static final String TEST_GRID_XML_OUT = "tmp/tmpsol/UC33brAvg_FM31_ABM_ELLB/nshm_grid_sources.xml";
	
	public static void main(String[] args) throws Exception {
		
//		// single solution
//		File file = new File(BR_AVG_SOL_PATH);
//		InversionFaultSystemSolution ifss = FaultSystemIO.loadInvSol(file);

//		File file = new File(COMP_SOL_PATH);
//		CompoundFaultSystemSolution cfss = CompoundFaultSystemSolution.fromZipFile(file);
//		LogicTreeBranch branch = LogicTreeBranch.fromFileName(TEST_BRANCH);
//		InversionFaultSystemSolution fss = cfss.getSolution(branch);
		
		
//		File binFile = new File("tmp/tmpsol/UC33brAvg_FM31_ABM_ELLB/sect_slips.bin"); 
//		double[] slips = MatrixIO.doubleArrayFromFile(binFile);
//		System.out.println(Arrays.toString(slips));
//		System.out.println(slips.length);
		
		double pp = 5.816678876916612E-5;
		double qq = 0.003;
		System.out.println(String.format("%.7e", pp));
		System.out.println(String.format("%.7g", pp));
		System.out.println(String.format("%g", pp));
		System.out.println(String.format("%.7g", qq));
		System.out.println(String.format("%f", qq));
		System.out.println(rates);
		
		String ref = "UCERF3.3 Branch Average: FM31_ABM_ELLB";
		File gridIn = new File(TEST_GRID_XML_IN);
		File gridOut = new File(TEST_GRID_XML_OUT);
		processGridFile(gridIn, gridOut, ref);
	}
	
	// Using strings for gird mag MFD parsing because of printed rounding errors
	// in source files
	private static final List<String> mags = Lists.newArrayList(
		"5.05","5.15","5.25","5.35","5.45","5.55","5.65","5.75","5.85","5.95",
		"6.05","6.15","6.25","6.35","6.45","6.55","6.65","6.75","6.85","6.95",
		"7.05","7.15","7.25","7.35","7.45","7.55","7.65","7.75","7.85");
	private static final List<Double> rates;
	
	static {
		double[] rateVals= new double[mags.size()];
		java.util.Arrays.fill(rateVals, 1.0);
		rates = Doubles.asList(rateVals);
	}
			
	
	/*
	 * Converts and condenses a grid file that accompanies a UC3 branch average
	 * solution. These are indexed according tothe RELM_GRIDDED region and can
	 * therefore be processed and output in order and maintin consistency with
	 * desired grid source output format.
	 */
	@SuppressWarnings("unchecked")
	private static void processGridFile(File in, File out, String reference) 
			throws DocumentException, IOException {
		
        GriddedRegion gr = RELM_RegionUtils.getGriddedRegionInstance();
        System.out.println(gr.getNodeCount());
        // file out
        Document docOut = DocumentHelper.createDocument();
        Element rootOut = docOut.addElement("GridSource");
        rootOut.addAttribute("reference", reference);
        Element mfd = rootOut.addElement("Defaults").addElement("MagFreqDist");
        mfd.addAttribute("type", "INCR");
        mfd.addAttribute("mags", "[" + Joiner.on(',').join(mags) + "]");
        mfd.addAttribute("rates",  "[" + Joiner.on(',').join(rates) + "]");
        Element nodes = rootOut.addElement("Nodes");
        
        // file in
        SAXReader reader = new SAXReader();
        Document docIn = reader.read(in);
        Element rootIn = docIn.getRootElement();
        Element nodeList = rootIn.element("MFDNodeList");
        Iterator<Element> it = nodeList.elementIterator("MFDNode");
        while (it.hasNext()) {
        	Element nodeIn = it.next();
        	int idx = Integer.parseInt(nodeIn.attributeValue("index"));
        	Location loc = gr.locationForIndex(idx);
        	List<Double> rates = processGridNode(nodeIn);
        	Element nodeOut = nodes.addElement("Node");
        	Iterable<String> rateStrs = Iterables.transform(
        		rates,
        		new Function<Double, String>() {
        			@Override public String apply(Double value) {
        				return value.equals(0.0) ? 
        					"0.0" : String.format("%.7g", value);
        			}
        		});
        	nodeOut.addAttribute("rates", "[" + Joiner.on(',').join(rateStrs) + "]");
        	nodeOut.addText(String.format(
        		"%.5f,%.5f,%.5f",
        		loc.getLongitude(), loc.getLatitude(), loc.getDepth()));
        }
        
        XMLWriter writer = new XMLWriter(
        	new FileWriter(out), OutputFormat.createPrettyPrint());
        writer.write(docOut);
        writer.close();
	}
	
	private static List<Double> processGridNode(Element e) {
		Element unassocPoints = e.element("UnassociatedFD").element("Points");
		Map<String, Double> unassocMap = mfdDatToMap(unassocPoints);
		Element subSeisElem = e.element("SubSeisMFD");
		List<Double> unassocRates = mapToRateList(unassocMap);
		if (subSeisElem == null) return unassocRates;
		Map<String, Double> subSeisMap = mfdDatToMap(subSeisElem.element("Points"));
		List<Double> subSeisRates = mapToRateList(subSeisMap);
		List<Double> totalRates = DataUtils.add(unassocRates, subSeisRates);
		return totalRates;
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Double> mfdDatToMap(Element e) {
		Set<String> magSet = Sets.newHashSet(mags);
		Map<String, Double> rateMap = Maps.newHashMap();
		List<Element> points = e.elements("Point");
		for (Element pt : points) {
			String m = String.format("%.2f", Double.parseDouble(pt.attributeValue("x")));
			if (magSet.contains(m)) {
				rateMap.put(m, Double.parseDouble(pt.attributeValue("y")));
			}
		}
		return rateMap;
	}
	
	private static List<Double> mapToRateList(Map<String, Double> map) {
		List<Double> rates = Lists.newArrayList();
		for (String m : mags) {
			rates.add(map.get(m));
		}
		return rates;
	}
	
	
}
