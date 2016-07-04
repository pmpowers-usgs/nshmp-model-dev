package gov.usgs.earthquake.model;

import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_POINT_WC94_LENGTH;
import static org.opensha2.internal.Parsing.addAttribute;
import static org.opensha2.internal.Parsing.addElement;
import static org.opensha2.internal.Parsing.enumValueMapToString;
import static org.opensha2.internal.SourceAttribute.DEPTH;
import static org.opensha2.internal.SourceAttribute.DIP;
import static org.opensha2.internal.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha2.internal.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceAttribute.RAKE;
import static org.opensha2.internal.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.internal.SourceAttribute.STRIKE;
import static org.opensha2.internal.SourceAttribute.WEIGHT;
import static org.opensha2.internal.SourceAttribute.WIDTH;
import static org.opensha2.internal.SourceElement.AREA_SOURCE_SET;
import static org.opensha2.internal.SourceElement.BORDER;
import static org.opensha2.internal.SourceElement.DEFAULT_MFDS;
import static org.opensha2.internal.SourceElement.SETTINGS;
import static org.opensha2.internal.SourceElement.SOURCE;
import static org.opensha2.internal.SourceElement.SOURCE_PROPERTIES;

import gov.usgs.earthquake.nshm.convert.MFD_Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.AreaSource.GridScaling;
import org.opensha2.geo.LocationList;
import org.opensha2.mfd.IncrementalMfd;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class AreaCreator {

	private List<SourceData> sourceData;
	private String name;
	private double weight;
	private String depthStr;

	public AreaCreator(String name, double weight, String depthStr) {
		this.name = name;
		this.weight = weight;
		this.depthStr = depthStr;
		sourceData = new ArrayList<>();
	}

	public static SourceData createSource(String name, LocationList border, MFD_Data mfdData,
			String mechStr, RuptureScaling rupScaling, GridScaling gridScaling, double strike) {
		return new SourceData(name, border, mfdData, mechStr, rupScaling, gridScaling, strike);
	}

	public static class SourceData {
		private String name;
		private LocationList border;
		private MFD_Data mfdData;
		private Double strike;
		private String mechStr;
		private RuptureScaling rupScaling;
		private GridScaling gridScaling;

		SourceData(String name, LocationList border, MFD_Data mfdData, String mechStr,
			RuptureScaling rupScaling, GridScaling gridScaling, Double strike) {
			this.name = name;
			this.border = border;
			this.mfdData = mfdData;
			this.mechStr = mechStr;
			this.rupScaling = rupScaling;
			this.gridScaling = gridScaling;
			this.strike = strike;
		}
	}

	public void addSource(SourceData source) {
		sourceData.add(source);
	}

	/*
	 * An AreaSourceSet can have default mfds - one shared depthModel; don't
	 * actually know if this is consistent with what users of area sources do -
	 * one mechMap per source
	 */

	public void export(Path dest) throws ParserConfigurationException, IOException,
			TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(AREA_SOURCE_SET.toString());
		addAttribute(NAME, name, root);
		addAttribute(WEIGHT, weight, root);
		doc.appendChild(root);

		Element settings = addElement(SETTINGS, root);

		// no default MFDs for now
		// Element mfdRef = addElement(DEFAULT_MFDS, settings);
		// grDat.appendTo(mfdRef, null);

		addSourceProperties(settings);

		for (SourceData source : sourceData) {
			Element srcElem = addElement(SOURCE, root);
			addAttribute(NAME, name, srcElem);
			source.mfdData.appendTo(srcElem, null);
			Element border = addElement(BORDER, srcElem);
			border.setTextContent(source.border.toString());
		}

		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(Files.newOutputStream(dest));

		trans.transform(source, result);
	}

	// source attribute settings
	private void addSourceProperties(Element settings) {
		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(MAG_DEPTH_MAP, depthStr, propsElem);
		addAttribute(RUPTURE_SCALING, NSHM_POINT_WC94_LENGTH, propsElem);
	}

}
