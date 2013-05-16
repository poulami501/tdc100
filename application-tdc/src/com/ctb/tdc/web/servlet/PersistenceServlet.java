package com.ctb.tdc.web.servlet;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.log4j.Logger;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import com.ctb.cat.web.client.CATServiceClient;
import com.ctb.tdc.web.utils.AuditFile;
import com.ctb.tdc.web.utils.Base64;
import com.ctb.tdc.web.utils.CATEngineProxy;
import com.ctb.tdc.web.utils.CalculatorDialog;
import com.ctb.tdc.web.utils.LoadTestUtils;
import com.ctb.tdc.web.utils.MemoryCache;
import com.ctb.tdc.web.utils.ServletUtils;
import com.ti.eps.emu84.testAgency.EmulatorComponent;
import com.ti.eps.ngiexamcalc.gui.ti30.CalcPaneTI30;

/** 
 * @author Tai_Truong
 * 
 * This supports response persistence and lifecycle events. 
 * New events are only accepted and persisted locally if prior events were acknowledged by 
 * upstream partner, otherwise an error occurs after a suitable wait/retry cycle. 
 * When prior events have been acknowledged by the TMS, new events generated by the client 
 * are acknowledged by the local servlet as soon as they are securely persisted locally, allowing 
 * the user to continue. Delay (and ultimately, in severe cases, interruption) of test thus only 
 * occur if upstream response time exceeds user 'think' time. To ensure that no responses are 
 * lost, an error is returned immediately in that case.
 */
public class PersistenceServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static Logger logger = Logger.getLogger(PersistenceServlet.class);
	private static final String osName = System.getProperty("os.name").toLowerCase();
	private static final String windowsPath = "//Application Data//Macromedia//Flash Player//macromedia.com//support//flashplayer//sys//#127.0.0.1";
	private static final String macPath = "//library//preferences//macromedia//Flash Player//macromedia.com//support//flashplayer//sys//#127.0.0.1";
	private static final String unixPath = "//.macromedia//Flash_Player//macromedia.com//support//flashplayer//sys";

	private static HashMap<String, String> audioResponseHash = new HashMap<String, String>();
	
	private static CalculatorDialog calculatorDialog30 = null;
	private static CalculatorDialog calculatorDialog84 = null;
	private static String calcType = "TI84";
	
	private static final String TDC_HOME = "tdc.home";
	private static final String RESOURCE_FOLDER_PATH = System.getProperty(TDC_HOME) + File.separator + 
		                             					"webapp" + File.separator + "resources";
	private static final String WEBINF_FOLDER_PATH = System.getProperty(TDC_HOME) + File.separator + 
														"webapp" + File.separator + "WEB-INF";
	private static final String PRODUCT_TYPE = System.getProperty("tdc.productType");

	private static native void nativeUpLevelWindow(final String windowName);
	
	static {
		if(osName.indexOf("mac") >= 0) {
            System.load(WEBINF_FOLDER_PATH + File.separator + "lib" + File.separator +"libAddressBook.jnilib");
            logger.info("Library loaded successfully"); 
        }
		
		showOkCalculator("TI30");
		showOkCalculator("TI84");
	}
	/**
	 * Constructor of the object.
	 */
	public PersistenceServlet() {
		super();
	}

	/**
	 * Initialization of the servlet. <br>
	 *
	 * @throws ServletException if an error occure
	 */
	public void init() throws ServletException {
		// do nothing
		//verifyServletSettings();
			// ensure native JNI library is loaded
	}

	/**
	 * Destruction of the servlet. <br>
	 */
	public void destroy() {
		super.destroy(); // Just puts "destroy" string in log
	}

	/**
	 * The doPost method of the servlet. <br>
	 *
	 * This method is called when a form has its tag value method equals to post.
	 * 
	 * @param request the request send by the client to the server
	 * @param response the response send by the server to the client
	 * @throws ServletException if an error occurred
	 * @throws IOException if an error occurred
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		//String method = request.getParameter("method");   // this line use with test.html
		String method = null; // this line is for release   
		if ((method != null) && (!method.equals(ServletUtils.NONE_METHOD))) {
			String xml = ServletUtils.buildPersistenceParameters(request,
					method);
			//handleEvent(response, method, xml, audioResponseString);
			handleEvent(response, method, xml, request);
		} else {
			doGet(request, response);
		}
	}

	/**
	 * The doGet method of the servlet. <br>
	 *
	 * This method is called when a form has its tag value method equals to get.
	 * 
	 * @param request the request send by the client to the server
	 * @param response the response send by the server to the client
	 * @throws ServletException if an error occurred
	 * @throws IOException if an error occurred
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String method = ServletUtils.getMethod(request);
		long startTime = System.currentTimeMillis();
		String xml = ServletUtils.getXml(request);
		handleEvent(response, method, xml, request);
		logger.info("PersistenceServlet: " + method + " took "
				+ (System.currentTimeMillis() - startTime) + "\n");
	}

	/**
	 * The handleEvent method of the servlet. <br>
	 * 
	 * call the method based on each event, return result response xml to client
	 * 
	 * @param HttpServletResponse response
	 * @param String method
	 * @param String xml
	 * @throws IOException 
	 */
	
	private void handleEvent(HttpServletResponse response, String method,
			String xml, HttpServletRequest request) throws IOException {
		String result = ServletUtils.OK;
		boolean validSettings = ServletUtils.validateServletSettings();
		Double abilityScore =0.0;
		Double sem = 0.0;
		String objScore = "0,0,0,0,0,0";

		// call method to perform an action only if servlet settings is valid
		if (!validSettings)
			result = ServletUtils.getServletSettingsErrorMessage();
		else if (method != null && method.equals(ServletUtils.VERIFY_SETTINGS_METHOD)) {
			result = verifyServletSettings();
		} else if (method != null && method.equals(ServletUtils.LOGIN_METHOD)) {
			result = login(xml);
		} else if (method != null && method.equals(ServletUtils.SAVE_METHOD))
		{
			System.out.println(" original save xml:"+xml);			
			if(ServletUtils.isCurSubtestAdaptive){
				String realId = null;
				String adsItemId = ServletUtils.parseAdsItemId(xml);

					
				if(adsItemId != null && !ServletUtils.NONE.equals(adsItemId)) {
	        		realId = (String) ContentServlet.itemSubstitutionMap.get(adsItemId);
	        		if(realId != null) {	        			
	        			xml = xml.replaceAll(adsItemId, realId);
	        		}
	        	}        	
				System.out.println(" replaced save xml:"+xml);
    			Boolean isStopCat = ServletUtils.isScoreSubtest(xml);
    			if (isStopCat) {
    				 abilityScore = CATServiceClient.getAbilityScore();
        			 sem = CATServiceClient.getSEM();
        			 objScore = CATServiceClient.getObjScore();	
        			
    				xml = LoadTestUtils.setAttributeValue("score.ability",abilityScore.toString(), xml);
    				xml = LoadTestUtils.setAttributeValue("score.sem",sem.toString(), xml);
    				xml = LoadTestUtils.setAttributeValue("score.objective",objScore, xml);
    				System.out.println("Student Stop: " + CATServiceClient.isStudentStop);
    				
    				//setting unscored_items=1 to detect student stop. 
    				//Changed: Need not do this now
    				if (CATServiceClient.isStudentStop){
    					xml = LoadTestUtils.setAttributeValue("number_of_unscored_items","1", xml);
    				}
	        		System.out.println("XML after Integrating: " + xml);
	        	}            
    			Integer itemRawScore = getItemRawScoreFromResponse(response, xml);
    			System.out.println("itemRawScore:"+itemRawScore);
    			String isCatOver = ServletUtils.parseCatOver(xml);
    			String itemresponse = ServletUtils.parseResponse(xml);
    			System.out.println("itemResponse: " + itemresponse);
    			String marked = ServletUtils.parseCatSave(xml);
    			//To check student stop and out of time
    			String isCatStop = ServletUtils.parseCatStop(xml);
	    		if(isCatOver != null && ("false".equals(isCatOver) || ServletUtils.NONE.equals(isCatOver))) {
	    			
	    			System.out.println("CurrentItem :"+ServletUtils.currentItem+":: itemid:"+realId);
	    			
		        	if(itemRawScore != null) {
		        		if(ServletUtils.currentItem == realId){
			        		try {
			        			System.out.println("inside condition!");
			        			if (itemresponse != null && itemresponse != "-" && marked != null && "1".equals(marked)){
			        	    		
			        					if((itemresponse.startsWith("undefined") || itemresponse.equals(""))&& isCatStop.startsWith("true")){
			        						CATEngineProxy.scoreCurrentItem(-9 , false);	
			        					}else if(isCatStop.startsWith("true") && (!itemresponse.startsWith("undefined") || !itemresponse.equals(""))){
			        						CATEngineProxy.scoreCurrentItem(itemRawScore.intValue(), true);	
			        					}else {
			        						CATEngineProxy.scoreCurrentItem(itemRawScore.intValue(), false);	
			        					}
			        	    		}
			        			
				        	} catch (Exception e) {
				        		System.out.println("CAT Over!");
				    			logger.info("CAT Over!");
				                //ServletUtils.writeResponse(response, ServletUtils.buildXmlErrorMessage("CAT OVER", "Ability: " + CATEngineProxy.getAbilityScore() + ", SEM: " + CATEngineProxy.getSEM(), "000"));
				    			ServletUtils.writeResponse(response, ServletUtils.buildXmlErrorMessage("CAT OVER", CATEngineProxy.getAbilityScore() + "|" + CATEngineProxy.getSEM() + "|" + CATEngineProxy.getObjScore() , "000"));
				    			//CATEngineProxy.deInitCAT();
				    			
				        	}
		        		}
		        	}	
	    		}
	        	result = save(response, xml, request);    
			}else{
				result = save(response, xml, request);     
			}
        }
		else if (method != null && method.equals(ServletUtils.FEEDBACK_METHOD))
			result = feedback(xml);
		else if (method != null
				&& method.equals(ServletUtils.UPLOAD_AUDIT_FILE_METHOD))
			result = uploadAuditFile(xml);
		else if (method != null
				&& method.equals(ServletUtils.WRITE_TO_AUDIT_FILE_METHOD))
			result = writeToAuditFile(xml);
		else if (method != null
				&& method.equals(ServletUtils.OK_CALCULATOR)) {
			calcType = request.getParameter("calcType");
			//result = showHideOkCalculator("N");
			//result = showOkCalculator(calcType);
			if(!calculatorDialog84.isVisible() && !calculatorDialog30.isVisible()) {
				showHideOkCalculator("N");
			} else {
				showHideOkCalculator("Y");
			}
		} else if (method != null
				&& method.equals(ServletUtils.SHOW_HIDE_OK_CALCULATOR)) {
			
			String isHideCalc = request.getParameter("isHidden");
			if("Y".equals(isHideCalc)) {
				if(calculatorDialog84.isVisible() || calculatorDialog30.isVisible()) {
					calculatorDialog84.setCalculatorPaused(true);
					calculatorDialog30.setCalculatorPaused(true);
				}
				showHideOkCalculator("Y");
			} else {
				if(calculatorDialog84.isCalculatorPaused() || calculatorDialog30.isCalculatorPaused()) {
					showHideOkCalculator("N");
				} else {
					showHideOkCalculator("Y");
				}
				calculatorDialog84.setCalculatorPaused(false);
				calculatorDialog30.setCalculatorPaused(false);
			}
			//result = showHideOkCalculator(request.getParameter("isHidden"));
		}
		else if (method != null
				&& method.equals(ServletUtils.CLOSE_OK_CALCULATOR))
			result = closeOkCalculator();
		else
			result = ServletUtils.ERROR;

		// return response to client
		if (result != null) {
			//System.out.println(result);
			String mseq = ServletUtils.parseMseq(xml);
			ServletUtils.writeResponse(response, result, mseq);
		}

	}

	private Integer getItemRawScoreFromResponse(HttpServletResponse response, String xml) {
    	try {
	    	//System.out.println(xml);
	    	if(xml.indexOf("<rv n=") >= 0) {
	    		String marked = ServletUtils.parseCatSave(xml);
	    		System.out.println(" sendcatsave : "+marked);
	    		if(marked != null && "1".equals(marked)) {
		    		String itemresponse = ServletUtils.parseResponse(xml);
		    		String correctResponse = (String) ContentServlet.itemCorrectMap.get(CATEngineProxy.getNextItem());
		    		if(correctResponse.equals(itemresponse)) {
			    		return new Integer(1);
			    	} else {
			    		return new Integer(0);
			    	}
	    		} else {
	    			return null;
	    		}
	    	} else {
	    		return null;
	    	}
    	} catch (Exception e) {
    		System.out.println("CAT Over!");
			logger.info("CAT Over!");
			ServletUtils.writeResponse(response, ServletUtils.buildXmlErrorMessage("CAT OVER", "Ability: " + CATEngineProxy.getAbilityScore() + ", SEM: " + CATEngineProxy.getSEM(), "000"));
            //CATEngineProxy.deInitCAT();
    	}
    	return null;
    }    
    
	/**
	 * The verifyServletSettings method of the servlet. <br>
	 * 
	 *  verify if values in tdc.properties are valid 
	 */
	private String verifyServletSettings() {
		String errorMessage = ServletUtils.OK;
		if (!ServletUtils.validateServletSettings()) {
			// return error message if values in properties file are invalid
			errorMessage = ServletUtils.getServletSettingsErrorMessage();
		} else {
			// properties file are valid, now check for TMS connection
			errorMessage = ServletUtils.httpClientGetStatus();
		}
		return errorMessage;
	}

	/**
	 * The login method of the servlet. <br>
	 * 
	 *  handle login request from client 
	 *  request login response xml from TMS   
	 *  process encryptionKey to memory cache
	 *  parse login response xml to determine roster id and restart data (if present)
	 *  return login response xml from TMS to client
	 * 
	 * @param String xml
	 */
	private String login(String xml) {
		String result = ServletUtils.ERROR;

		try {
			// sent login request to TMS
			//logger.info("***** login request");
			result = ServletUtils.httpClientSendRequest(
					ServletUtils.LOGIN_METHOD, xml);
			//System.out.println("loginresponse xml "+ result);
			String userHome = System.getProperty("user.home");
			//Performing the FlashCookie Copy operation to avoid the flash security pop-up

			File flashCookieSource = new File(getServletContext().getRealPath(
					"/")
					+ "//settings.sol");
			//System.out.println("flash Cookie Source exists" + flashCookieSource.exists());
			InputStream in = null;
			try {
				in = new FileInputStream(flashCookieSource);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				System.out.println(e);
				e.printStackTrace();
			}

			File flashCookieTarget = null;
			if (osName.indexOf("win") >= 0) {
				System.out.println("Inside If");
				boolean successWindows = new File(userHome + windowsPath)
						.mkdirs();
				flashCookieTarget = new File(userHome + windowsPath
						+ "//settings.sol");
			} else if (osName.indexOf("mac") >= 0) {
				boolean successMac = new File(userHome + macPath).mkdirs();
				flashCookieTarget = new File(userHome + macPath
						+ "//settings.sol");
				if (!flashCookieTarget.exists())
					flashCookieTarget.createNewFile();
			} else {
				boolean successUnix = new File(userHome + unixPath).mkdirs();
				flashCookieTarget = new File(userHome + unixPath
						+ "//settings.sol");
				if (!flashCookieTarget.exists())
					flashCookieTarget.createNewFile();
			}

			OutputStream outFile = null;

			try {
				outFile = new FileOutputStream(flashCookieTarget);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					outFile.write(buf, 0, len);
				}
				in.close();
				outFile.close();
				//	System.out.println("FileCopied");
			} catch (Exception e) {
				e.printStackTrace();
			}
			//System.out.println("*******user Home********" + userHome);

			if (ServletUtils.isLoginStatusOK(result)) {
				// process encryptionKey to memory cache
				ServletUtils.processContentKeys(result);
				
				//if(ServletUtils.isCurSubtestAdaptive){
					ServletUtils.getConsolidatedRestartData(result);
				//}
				//System.out.println("login landing item: "+ServletUtils.landingItem);
				//moved this call to Content Servlet for calling decryption only for adaptive subtest
				//ContentFile.decryptDataFiles();  
				result = getCustomerInformation(result);
				// if file exist handle restart  
				String fileName = ServletUtils.buildFileName(xml);
				if (AuditFile.exists(fileName)) {
					// handle restart here in phase 2
				}
				//logger.info("Login successfully.");    
				processLoginResponse(result);
			} else {
				//logger.error("TMS returns error in login() : " + result);   
			}
			
			File speexFile = new File(getServletContext().getRealPath("/")
					+ "//streams//");
				File[] files = speexFile.listFiles();
				System.out.println("files length" + files.length);
				if(files.length>0){
					for (int i = 0; i < files.length; i++) {
						files[i].delete();
					}
				}
			

								
		}

		catch (Exception e) {
			logger.error("Exception occured in login() : "
					+ ServletUtils.printStackTrace(e));
			result = ServletUtils.buildXmlErrorMessage("", e.getMessage(), "");
		}
		return result;
	}

	/**
	 * The feedback method of the servlet. <br>
	 * 
	 *  handle feedback request from client
	 *  return feedback response xml from TMS to client
	 *  
	 * @param String xml
	 */
	private String feedback(String xml) {
		String result = ServletUtils.ERROR;
		try {
			// sent feedback request to TMS
			//logger.info("***** studentFeedback request");
			result = ServletUtils.httpClientSendRequest(
					ServletUtils.FEEDBACK_METHOD, xml);
		} catch (Exception e) {
			logger.error("Exception occured in feedback() : "
					+ ServletUtils.printStackTrace(e));
			result = ServletUtils.buildXmlErrorMessage("", e.getMessage(), "");
		}
		return result;
	}

	/**
	 * The save method of the servlet. <br>
	 *   
	 *  verify the acknowledge from TMS, checking based on values settings in tdc.properties   
	 *  if acknowledge checking failed, return error to client '<ERROR />'
	 *  if acknowledge checking passed, write response to audit file (if save response), 
	 *  return ack to client, send request to TMS, on response from TMS, change ack in memory cache.
	 *  
	 * @param HttpServletResponse response
	 * @param String xml
	 */
	private String save(HttpServletResponse response, String xml,
			HttpServletRequest request) {

		String result = null; // must set to null to prevent sending response twice
		String errorMessage = null;
		// parse request xml for information
		String lsid = ServletUtils.parseLsid(xml);
		String mseq = ServletUtils.parseMseq(xml);
		String rosterId = lsid.split(":")[0];
		String itemId = ServletUtils.parseItemId(xml);
		String itemResponse = ServletUtils.parseResponse(xml);
		String fileName = rosterId + "_" + itemId;
		String decodedXml = null;
		String startAnswerTag = null;
		String endAnswerTag = null;
		String decodedItemResponse = null;
		String replacedItemResponse = null;
		String paramXml = xml;
		boolean containsAudioResponse = false;
		String audioResponseString = null;
		String base64String = null;

		try {

			decodedXml = URLDecoder.decode(xml);
			decodedItemResponse = URLDecoder.decode(itemResponse);
			
			String[] answersTags = decodedItemResponse.split(fileName);

			if (answersTags.length > 1) {

				startAnswerTag = URLEncoder.encode(answersTags[0]);
				endAnswerTag = URLEncoder.encode(answersTags[1]);
				
				base64String = generateBase64String(fileName);

				if(base64String != "") {	
					//prepare answer string with actual audio data
					replacedItemResponse = startAnswerTag + "" + base64String + ""
							+ endAnswerTag;
				}
				else {
					replacedItemResponse = base64String;
				}
				
				//replace the audio file name with actual audio data
				xml = decodedXml.replaceAll(decodedItemResponse,
						replacedItemResponse);

				containsAudioResponse = true;
			}

			boolean isEndSubtest = ServletUtils.isEndSubtest(xml);

			MemoryCache memoryCache = MemoryCache.getInstance();

			// log an entry into audit file
			boolean hasResponse = ServletUtils.hasResponse(xml);
			if (hasResponse) {
				if (containsAudioResponse) {
					//	System.out.println("containsaudioresponse");
					AuditFile.log(ServletUtils.createAuditVO(paramXml,
							hasResponse));
				} else {

					AuditFile.log(ServletUtils.createAuditVO(xml,
							hasResponse));
				}
			}

			result = save(xml);

		} catch (Exception e) {
			logger.error("mseq " + mseq + ": Exception occured in save() : "
					+ e.getMessage());
			e.printStackTrace();
			errorMessage = ServletUtils
					.getErrorMessage("tdc.servlet.error.noAck");
			result = ServletUtils.buildXmlErrorMessage("", errorMessage, "");
		}
		
		if (fileName != null) {
			if (result == ServletUtils.OK) {
				if (base64String != null) {
					File speexFile = new File(getServletContext().getRealPath(
							"/")
							+ "//streams//" + fileName + ".spx");

					if (speexFile.exists()) {
						speexFile.delete();
					}
				}
			}
		}
		return result;
	}

	private static String save(String xml) throws Exception {
		String result = null;
		MemoryCache memoryCache = MemoryCache.getInstance();
		String lsid = ServletUtils.parseLsid(xml);
		String mseq = ServletUtils.parseMseq(xml);
		boolean isEndSubtest = ServletUtils.isEndSubtest(xml);
		// send request to TMS
		if (memoryCache.getSrvSettings().isTmsPersist()) {
			// message was added to pending list in cache,
			// send save request to TMS
			String tmsResponse = "";
			
			//logger.info("mseq " + mseq + ": persistence request");
			tmsResponse = ServletUtils.httpClientSendRequest(ServletUtils.SAVE_METHOD, xml);
			if (isEndSubtest) {
				result = tmsResponse;
				ServletUtils.isRestart = false;
				if(ServletUtils.isCurSubtestAdaptive){
					CATEngineProxy.deInitCAT();
				}
			} else {
				if (ServletUtils.isStatusOK(tmsResponse)) {
					result = ServletUtils.OK;
				}
			}
			boolean hasLev = ServletUtils.hasLev(xml);
			if(hasLev) {
				//space out lifecycle events a little bit
				Thread.sleep(1000);
			}
		}
		return result;
	}

	/**
	 * The writeToAuditFile method of the servlet. <br>
	 * 
	 * write xml model content to audit file
	 * 
	 * @param String xml
	 */
	private String writeToAuditFile(String xml) {
		String result = ServletUtils.ERROR;
		String errorMessage = null;
		System.out.println("writeToAuditFile....");
		try {
			// truncate the file if it is bigger than 200 KB before write model content
			String fileName = ServletUtils.buildFileName(xml);
			if (ServletUtils.isFileSizeTooBig(fileName)) {
				//logger.info("Audit file is too big (> 200KB), file will be truncated before writing model data.");
				AuditFile.deleteLogger(fileName);
			}

			// write model content to audit file
			AuditFile.log(ServletUtils.createAuditVO(xml, false));

			// sent writeToAuditFile request to TMS
			//logger.info("***** uploadAuditFile request");
			ServletUtils.httpClientSendRequest(
					ServletUtils.WRITE_TO_AUDIT_FILE_METHOD, xml);
			result = ServletUtils.OK; // nothing return from TMS
		} catch (Exception e) {
			logger.error("Exception occured in writeToAuditFile() : "
					+ ServletUtils.printStackTrace(e));
			errorMessage = ServletUtils
					.getErrorMessage("tdc.servlet.error.writeToAuditFileFailed");
			result = ServletUtils.buildXmlErrorMessage("", errorMessage, "");
		}
		return result;
	}

	/**
	 * The uploadAuditFile method of the servlet. <br>
	 * 
	 * upload the audit file to TMS and delete the file from local storage
	 * 
	 * @param String xml
	 */
	private String uploadAuditFile(String xml) {
		synchronized (ServletUtils.client) {
			String result = ServletUtils.ERROR;
			String errorMessage = null;
			int responseCode = HttpStatus.SC_OK;

			MemoryCache memoryCache = MemoryCache.getInstance();
			if (memoryCache.getSrvSettings().isTmsAuditUpload()) {
				// get the audit file to upload
				String fileName = ServletUtils.buildFileName(xml);
				File file = new File(fileName);

				if (file.exists()) {
					// get checksum value
					long sumValue = ServletUtils.getChecksum(file);
					if (sumValue == -1L) {
						//logger.error("Checksum error.");
						return ServletUtils.ERROR;
					}

					// setup URL parameters
					String tmsURL = ServletUtils
							.getTmsURLString(ServletUtils.UPLOAD_AUDIT_FILE_METHOD);
					tmsURL += "?" + ServletUtils.XML_PARAM + "=" + xml;
					tmsURL += "&" + ServletUtils.CHECKSUM_PARAM + "="
							+ sumValue;

					//PostMethod filePost = new PostMethod(tmsURL);
					HttpPost filePost = new HttpPost(tmsURL);
					try {
						FileEntity reqEntity = new FileEntity(file, "text/plain");
						filePost.setEntity(reqEntity);
						
						// set multipart request
						/*Part[] parts = { new FilePart(
								ServletUtils.AUDIT_FILE_PARAM, file) };
						filePost.setRequestEntity(new MultipartRequestEntity(
								parts, filePost.getParams()));*/
						
						
						HttpResponse res  = ServletUtils.client.execute(filePost);
						
					
						// delete local file when upload successfully 
						if (res.toString().contains("OK")) {
							/*InputStream isPost = res.
							BufferedReader in = new BufferedReader(
									new InputStreamReader(isPost));
							String inputLine = null;*/
							
							String tmsResponse = res.toString();
							/*while ((inputLine = in.readLine()) != null) {
								tmsResponse += inputLine;
							}
							in.close();*/
							
							
							// if OK return from TMS, delete local file
							if (ServletUtils.isStatusOK(tmsResponse)) {
								//logger.info("Upload audit file successfully");
								if (AuditFile.deleteLogger(fileName)) {
									//logger.info("Delete audit file successfully");
									result = ServletUtils.OK;
								} else {
									//logger.error("Failed to delete audit file");
									errorMessage = ServletUtils
											.getErrorMessage("tdc.servlet.error.deleteAuditFileFailed");
									result = ServletUtils.buildXmlErrorMessage(
											"", errorMessage, "");
								}
							} else {
								//logger.error("Failed to upload audit file, tmsResponse=" + tmsResponse);
								errorMessage = ServletUtils
										.getErrorMessage("tdc.servlet.error.uploadFailed");
								result = ServletUtils.buildXmlErrorMessage("",
										errorMessage, "");
							}
						} else {
							//logger.error("Failed to upload audit file, responseCode=" + HttpStatus.getStatusText(responseCode));
							result = ServletUtils.buildXmlErrorMessage("",
									HttpStatus.getStatusText(responseCode), "");
						}
					} catch (Exception e) {
						logger
								.error("Exception occured in uploadAuditFile() : "
										+ ServletUtils.printStackTrace(e));
						errorMessage = ServletUtils
								.getErrorMessage("tdc.servlet.error.uploadFailed");
						result = ServletUtils.buildXmlErrorMessage("",
								errorMessage, "");
					} finally {
						
					}
				} else {
				}
			} else {
				// file was already uploaded by another thread
				result = ServletUtils.OK;
			}

			return result;
		}
	}

	/***
	 * generateBase64String() helps in generating the base64 encoded String from 
	 * the Audio File
	 * 
	 * 
	 * @return
	 */
	private String generateBase64String(String fileName) {
	
	//System.out.println("file passed in generateBase64String : " +  fileName);
		String base64EncodedString = "";
		try {
			File file = new File(getServletContext().getRealPath("/")
					+ "//streams//" + fileName + ".spx");
			
			if (!file.exists()) {
				
				return base64EncodedString;//file does not exist so return empty string
			}

			FileInputStream fis = new FileInputStream(file);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			try {
				for (int readNum; (readNum = fis.read(buf)) != -1;) {
					bos.write(buf, 0, readNum); //Initial run so no doubt here it's 0
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			bos.close();
			fis.close();

			byte[] bytes = bos.toByteArray();
			base64EncodedString = Base64.encode(bytes);

		} catch (Exception e) {
			logger.error("Base64String Not Generated");
			e.printStackTrace();
		}
		return base64EncodedString;

	}
	/**
	 * 
	 * Method created to populate the content Download URI from login response
	 * @param loginResponse
	 */
	private void  processLoginResponse(String loginResponse){
		//System.out.println("Process Login Response"  + loginResponse);
		MemoryCache memCache = MemoryCache.getInstance();
		HashMap contentDownloadMap = new HashMap();
		org.jdom.Document loginReponseDocument = null;
		System.out.println("processLoginResponse");
		SAXBuilder saxBuilder = new SAXBuilder();
		try {
			loginReponseDocument = saxBuilder.build(new ByteArrayInputStream(loginResponse.getBytes()));
		} catch (JDOMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		org.jdom.Element element = (org.jdom.Element) loginReponseDocument.getRootElement().getChild("login_response");
		element = element.getChild("manifest");
		if (element != null){
			List subtestList = element.getChildren("sco");
			for (int i=0; i < subtestList.size(); i++){

				element = (org.jdom.Element) subtestList.get(i);
				contentDownloadMap.put(element.getAttributeValue("adsid"),  element.getAttributeValue("contentURI"));

			}

			memCache.setContentDownloadMap(contentDownloadMap);

			CATServiceClient.processCATLoginElement(element);
		}
	}
	
	private String  getCustomerInformation(String loginResponse){
		org.jdom.Document loginReponseDocument = null;
		System.out.println("getCustomerInformation");
		SAXBuilder saxBuilder = new SAXBuilder();
		String result = null;
		String resultXml = null;		
		try {
			loginReponseDocument = saxBuilder.build(new ByteArrayInputStream(loginResponse.getBytes("UTF-8")));
		} catch (JDOMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			if(PRODUCT_TYPE != null && PRODUCT_TYPE.equals("OKLAHOMA")) {
				loginReponseDocument.getRootElement().getChild("login_response").setAttribute("isOK", "T");
			}
			else {
				loginReponseDocument.getRootElement().getChild("login_response").setAttribute("isOK", "F");
			}
		
		 resultXml = new XMLOutputter().outputString(loginReponseDocument);
		 result = resultXml.substring(resultXml.indexOf("<tmssvc_response"));
		 
		 return result;
	}
	
	public static String showOkCalculator(final String calcType) {
		try {
            //Schedule a job for the event-dispatching thread:
	        //creating and showing this application's GUI.
			if("TI84".equals(calcType)) {
				if(calculatorDialog84 == null || !calculatorDialog84.isCalculatorRunning()) {
			        javax.swing.SwingUtilities.invokeLater(new Runnable() {
			            public void run() {
			            	createAndShowTI84();
				        }
			        });
				} else {
					calculatorDialog84.dispose();
				}
			} else {
				if(calculatorDialog30 == null || !calculatorDialog30.isCalculatorRunning()) {
			        javax.swing.SwingUtilities.invokeLater(new Runnable() {
			            public void run() {
			            	createAndShowTI30();	
				        }
			        });
				} else {
					calculatorDialog30.dispose();
				}
			}
	        return ServletUtils.OK;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ServletUtils.ERROR;
	}
	
	public static String showHideOkCalculator(String hidden) {
		try {
			//Schedule a job for the event-dispatching thread:
	        //creating and showing this application's GUI.
			if("TI84".equals(calcType)) {
				if(calculatorDialog84 != null && calculatorDialog84.isCalculatorRunning()) {
					if("Y".equals(hidden)) {
						calculatorDialog84.setVisible(false);
					} else {
						calculatorDialog84.setVisible(true);
						String windowName = calculatorDialog84.getTitle();
						if(osName.indexOf("mac") >= 0) {
				            logger.info("Calling native window method");
				            nativeUpLevelWindow(windowName);
				        }
					}
				}
			} else {
				if(calculatorDialog30 != null && calculatorDialog30.isCalculatorRunning()) {
					if("Y".equals(hidden)) {
						calculatorDialog30.setVisible(false);
					} else {
						calculatorDialog30.setVisible(true);
						String windowName = calculatorDialog30.getTitle();
						if(osName.indexOf("mac") >= 0) {
				            logger.info("Calling native window method");
				            nativeUpLevelWindow(windowName);
				        }
					}
				}
			}
	        return ServletUtils.OK;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ServletUtils.ERROR;
	}
	
	public static String closeOkCalculator() {
		try {
			if(calculatorDialog84 != null && calculatorDialog30 != null) {
				showHideOkCalculator("Y");
			}
			return ServletUtils.OK;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ServletUtils.ERROR;
	}
	
	/**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowTI84() {
    	
    	String javaLibPath = System.getProperty("java.library.path");
		System.setProperty("java.library.path", WEBINF_FOLDER_PATH + File.separator + "lib");
    	try {
			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

    	JFrame jFrame = new JFrame();
    	calculatorDialog84 = new CalculatorDialog(jFrame, ServletUtils.GRAPHIC_CALCULATOR);
    	calculatorDialog84.setAlwaysOnTop(true);
    	calculatorDialog84.setResizable(false);
    	//calculatorDialog84.setIconImage(new ImageIcon(RESOURCE_FOLDER_PATH + File.separator + "calc.png").getImage());
    
    	EmulatorComponent emu = new EmulatorComponent(jFrame);
        emu.setFaceSize(EmulatorComponent.MEDIUM);
        emu.ResetEmulator();
        
        //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel emptyLabel = new JLabel("");
        //emptyLabel.setPreferredSize(new Dimension(175, 100));
        calculatorDialog84.getContentPane().add(emptyLabel, BorderLayout.CENTER);

        //Display the window.
        calculatorDialog84.getContentPane().add(emu);
        calculatorDialog84.pack();
        
        // Set the JFrame at middle of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Point middle = new Point(screenSize.width / 2, screenSize.height / 2);
        Point newLocation = new Point(middle.x - (calculatorDialog84.getWidth() / 2), 
                                      middle.y - (calculatorDialog84.getHeight() / 2));
        calculatorDialog84.setLocation(newLocation);
        //calculatorDialog.setVisible(true);
        
        System.setProperty("java.library.path", javaLibPath);
    	try {
			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
    }
    
    private static void createAndShowTI30() {
        
        JFrame jframe = new JFrame();
    	calculatorDialog30 = new CalculatorDialog(jframe, ServletUtils.SCIENTIFIC_CALCULATOR);
                
    	calculatorDialog30.setAlwaysOnTop(true);
    	calculatorDialog30.setResizable(false);
    	//calculatorDialog30.setIconImage(new ImageIcon(RESOURCE_FOLDER_PATH + File.separator + "calc.png").getImage());
    	calculatorDialog30.setSize(300, 600);
    	
        CalcPaneTI30 emu = new CalcPaneTI30(calculatorDialog30.getContentPane());
        calculatorDialog30.add(emu, BorderLayout.CENTER);

        // Set the JFrame at middle of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Point middle = new Point(screenSize.width / 2, screenSize.height / 2);
        Point newLocation = new Point(middle.x - (calculatorDialog30.getWidth() / 2), 
                                      middle.y - (calculatorDialog30.getHeight() / 2));
        calculatorDialog30.setLocation(newLocation);
        
        calculatorDialog30.setVisible(true);
        calculatorDialog30.setVisible(false);
    }
}