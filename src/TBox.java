import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.snu.ids.ha.ma.MExpression;
import org.snu.ids.ha.ma.MorphemeAnalyzer;
import org.snu.ids.ha.ma.Sentence;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * @author abhishek
 *
 */
public class TBox {
	/**
	 * @param args
	 * @throws Exception 
	 */
		//Starting point
	   public static void main(String args[]) throws Exception 
	   {
		   String inputfileName = "input.txt";
		   Util ut = new Util();
		   ArrayList<ArrayList<String>> inputList = ut.readTBoxInputList(inputfileName);
		   String outputFileName = "output.txt";
		   int MAXPAGES = 10;
		   int results_per_page = 20;
		   TBoxAgent tbox = new TBoxAgent(outputFileName, results_per_page, MAXPAGES);
		   tbox.runList(inputList);
		}
}

class Util
{
	@SuppressWarnings("resource")
	ArrayList<ArrayList<String>> readTBoxInputList(String fileName)
	{
		
		ArrayList<ArrayList<String>>inputList = new ArrayList<ArrayList<String>>();
		BufferedReader br = null;
    	try
    	 {
    		 String sCurrentLine;
    		 br = new BufferedReader(new FileReader(fileName));
    		 while ((sCurrentLine = br.readLine()) != null) 
    		 {
    			 ArrayList<String>line = new ArrayList<String>();
    			 String l[]= sCurrentLine.split("\t");
    			 line.add(l[0]);
    			 line.add(l[1]);
    			 //line.add(l[2]);
    			 inputList.add(line);
    		 }
    	 }catch(Exception ex)
    	 {
    	     ex.printStackTrace();
    	 }
    	
	    return inputList;
	}
}

class TBoxAgent{
	
	Statement sQ;
	int CountSum=0;
	SearchAgent sa;
	Statement Q;
	String OUTPUT_TRUSTRANK="";
	String FILE;
	MorphemeAnalyzer mo=null;
	
	public TBoxAgent(String FILE, int results_per_page, int MAXPAGES)
	{	
		this.FILE=FILE;
		this.mo = new MorphemeAnalyzer();
		this.sa = new SearchAgent(results_per_page, MAXPAGES);
	}
	
	//run the entire file of statements and display trustscore of top 3 alternatives for doubtful unit of each statement
    void runList(ArrayList<ArrayList<String>>inputList) throws Exception
    {
    	PrintWriter pw_result = new PrintWriter(new BufferedWriter( new FileWriter(this.FILE)), true);
    	String statement, DU, answer;
    	for(int i=0; i<inputList.size(); i++)
    	{
    		statement = inputList.get(i).get(0);
    		DU = inputList.get(i).get(1);
    		this.runStatement(statement, DU.trim(), this.mo);
    	}
    	pw_result.println(OUTPUT_TRUSTRANK);
    	pw_result.close();
    }
        
    //run each statement and find top 3 best alternatives for doubtful unit
    void runStatement(String statement, String DU, MorphemeAnalyzer mo) throws Exception
    {
    	String corrected_string="";
    	try {
    		
			this.Q = new Statement(statement, DU, mo);
		} catch (Exception e) {
			e.printStackTrace();
		}
    	LinkedHashMap<String, Double>candidateList = this._analyze();
    	
        int top3 = 0;
        int len3 = candidateList.size();
        for(Map.Entry<String, Double> entry : candidateList.entrySet())
        {
        	  String key = entry.getKey();
        	  Double value = entry.getValue();
        	  corrected_string  = statement.replaceAll(DU, key)+ ", "+value+"\n";
        	  OUTPUT_TRUSTRANK += corrected_string;               	  
        	  top3++;
        	  
        	  if(len3>=3){
        		  if(top3==3)break;
        	  }      	  
        }
    }
	
	LinkedHashMap<String, Double> getCandidates(String statement, String DU, MorphemeAnalyzer mo) throws UnsupportedEncodingException, FileNotFoundException, IOException
	{
		try {
			this.Q = new Statement(statement, DU,  mo);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this._analyze();
	}

	//analyze each statement in order to retrieve trustscores
	LinkedHashMap<String, Double> _analyze() throws UnsupportedEncodingException, FileNotFoundException, IOException
	{
		
		LinkedHashMap<String, String> results = null;
		try{			
			//results = this.sa.brochetteSearch(this.Q.TU);	
			results = this.sa.brochetteSearch(this.Q);		
			this.Q.addSRRListBatch(results);			
        }catch(Exception e)
        {
            System.out.println("Search Resource Not Found");
            e.printStackTrace();
        }       
        this.Q.filterDataType();
        this.Q.getScores();
        return this.Q.candidateTRank();
	}
}

//Searching results from brochette database
class SearchAgent{
	int results_per_page;
	int MAXPAGES;
	static final String BROCHETTE_SEARCH_URL = "http://211.109.9.10:8080/brochette-api/v0.1/search.json?keyword=";
	public SearchAgent(int results_per_page, int MAXPAGES){
		this.results_per_page=results_per_page;
		this.MAXPAGES=MAXPAGES;
	}
	LinkedHashMap<String, String> brochetteSearch(Statement Q)
	{
		String keyw = Q.TU;
		LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
		
		String arrsplit[] = keyw.trim().split("\\s+");
		if(arrsplit.length>0){
			keyw = keyw.replaceAll("\\s+", "+").trim();	
		}
		String mUrl = BROCHETTE_SEARCH_URL+keyw+"&count="+this.MAXPAGES+"&separated=true&search%E2%80%90field=title";
		
		try{
		URL url = new URL(mUrl);
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));

		LinkedList<String> lines = new LinkedList<String>();
		String readLine = new String();		
		while ((readLine = in.readLine()) != null) {lines.add(readLine);}
		
		String result = new String();
		for (String line : lines) {result += line;}
		
		
		if(result.charAt(0)=='[')result = result.substring(1); 
		if(result.charAt(result.length()-1)==']')result = result.substring(0, result.length()-1); 
		
		ArrayList<String> results = new ArrayList<String>();
		String [] splited_result = result.split("},");
		int i= 1;
		
		for(String temp:splited_result){
			if(i != splited_result.length){
				results.add(temp+"}");
			}else{
				results.add(temp);
			}
			i++;
		}
		
		for(String each_json : results)
		{
			JSONParser jsonParser = new JSONParser();
	        JSONObject jsonObject = (JSONObject) jsonParser.parse(each_json);
	        
	        String Title = (String) jsonObject.get("title");
	    	
	    	JSONArray content_by_line = (JSONArray) jsonObject.get("content");
	    	Iterator<String> iterator = content_by_line.iterator();
	    	
	    	int counter=0;
	    	String val = "";	    	
			while (iterator.hasNext()) 
			{
				String nextSentence = iterator.next().trim();
				POSNERterm kkmstmt1 = new POSNERterm();
		    	LinkedHashMap<String, String> POSDict_stmt1 = new LinkedHashMap<String, String>();				
				kkmstmt1 = Q._getTagsStatementKorean(nextSentence);
				POSDict_stmt1  = kkmstmt1.getPOSDict();
				
				for(Map.Entry<String, String> entry1 : Q.NERStatement.entrySet())
		        {
		        	  String key1 = entry1.getKey();
		        	  String value1 = entry1.getValue();
		        	  if(POSDict_stmt1.containsKey(key1)){
		        		  if(POSDict_stmt1.get(key1).equalsIgnoreCase(value1)){
		        			  val =val+ nextSentence+" ";
		        			  break;
		        		  }
		        	  }
		        }
				
				counter++;
				if(counter==this.results_per_page)
				{
					if(val.length()>0){
						linkedHashMap.put(Title, val);
					}
					break;
				}
			}
		}
		}catch(Exception e){
			System.out.println();
			System.out.println("Statement: "+keyw);
			System.out.println("errorCode: ResourceNotFound");
			linkedHashMap=null;			
		}
        return linkedHashMap;
	}
}

class Statement{
	String DU="", statement="", TU="", DUNER="", DUPOS="";
	int DUCount=0, N=0;

	Multimap<String, String> POSDict;
	
	ArrayList<String> termList = new ArrayList<String>();
	ArrayList<String> termSet;
	Map<ArrayList, ArrayList> SRRList;
	ArrayList<String> sentenceList;
	                                                                                                                     
	LinkedHashMap<String, Double> RC;
	LinkedHashMap<String, Double> RQR;
	LinkedHashMap<String, Double> Rrank;
	LinkedHashMap<String, Double> TD;
	LinkedHashMap<String, Double> TLC;
	LinkedHashMap<String, String> NERStatement;
	LinkedHashMap<String, Double> SC;
	LinkedHashMap<String, Double> AlterRankScore;	
	MorphemeAnalyzer ma=null;
	Util ut1 = null;
	String OUTPUT;
	
	double ContSum;

	public Statement(){
		
	}
	
	 public Statement(String statement, String DU, MorphemeAnalyzer mm) throws Exception {
		 
			this.ContSum=0;
			this.ma = mm;
			this.DU= DU.toLowerCase().trim();
			if(this.DU.isEmpty())
			{
				this.DUCount=0;
			}
			else
			{
				this.DUCount = this.DU.split("\\s+").length;
			}
			this.statement= statement.toLowerCase();
			this.TU= this._getTU().toLowerCase().trim();
			this.NERStatement = new LinkedHashMap<String, String>();
			POSNERterm POSNERclass = new POSNERterm();
			POSNERclass = this._getTagsStatementKorean(statement);
			this.NERStatement = POSNERclass.getPOSDict();
			//POSNERclass.gettermList();

			POSNERclass = this._getTagsStatementKorean(this.DU);
			LinkedHashMap<String, String> POSDict2 = POSNERclass.getPOSDict();
			this.DUPOS = this.getLastKey(POSDict2);
			this.DUNER = "";

			this.termSet= new ArrayList<String>();
			this.N=0;
			this.SRRList = new LinkedHashMap<ArrayList, ArrayList>(); 
			this.sentenceList = new ArrayList<String>();
			this.RC = new LinkedHashMap<String, Double>();
			this.RQR = new LinkedHashMap<String, Double>();
			this.Rrank = new LinkedHashMap<String, Double>();
			this.TD = new LinkedHashMap<String, Double>();
			this.TLC = new LinkedHashMap<String, Double>();
			
			this.SC = new LinkedHashMap<String, Double>();
			this.POSDict = ArrayListMultimap.create();
			this.AlterRankScore = new LinkedHashMap<String, Double>();
			this.OUTPUT="";	 
	}
	 //retrieve the last key from linkedhasmap
	 String getLastKey(LinkedHashMap<String, String>myMap) {
		  String out = null;
		  for (String key : myMap.keySet()) {
		    out = myMap.get(key);
		  }
		  return out;
		}
		

	 //process each statement in terms of pos tagging
    void addSRRListBatch(LinkedHashMap<String, String> SRRList) throws Exception
    {
    	try{
    	if(SRRList!=null){
    	this.N += SRRList.size();
    	
    	
        
        for (String key : SRRList.keySet())
        {      		
        	LinkedHashMap<String, String> POSDict4 = new LinkedHashMap<String, String>();
        	ArrayList<String> termList1 = new ArrayList<String>();	
        	
        	POSNERterm kkm = new POSNERterm();
			kkm = this._getTagsStatementKorean(key);
			POSDict4 = kkm.getPOSDict();
			termList1 = kkm.gettermList();
			
            for(String k : POSDict4.keySet())
            { 
	                String keyword = k.toLowerCase();
	                this.POSDict.put(keyword, POSDict4.get(k));
            }
            LinkedHashMap<String, String> POSDict5 = new LinkedHashMap<String, String>();
        	ArrayList<String> termList2 = new ArrayList<String>();	
        	POSNERterm kkm1 = new POSNERterm();
            kkm1 = this._getTagsStatementKorean(SRRList.get(key));
            POSDict5 = kkm1.getPOSDict();
            termList2 = kkm1.gettermList();
            for(String k1 : POSDict5.keySet())
            {
            	
	                String keyword1 = k1.toLowerCase();
	                this.POSDict.put(keyword1, POSDict5.get(k1));
            }
            this.SRRList.put(termList1, termList2);
        }
    	}
    	}catch(Exception e){
    		System.out.println("SRRList is null");
    	}
    }
    
    //calculate trustscore for each statement
    void getScores()
    {
    	List<String> removeTermList = new ArrayList<String>();
        this.ContSum = 0.0;
        for (Entry<ArrayList, ArrayList> entry : this.SRRList.entrySet()) 
        {
			ArrayList<String> termsTitle = entry.getKey();
			ArrayList<String> termsDesc = entry.getValue();
			for(int pp=0;pp<termsTitle.size();pp++)
			{
				if(termsTitle.get(pp).contains(this.DU)){
					this.ContSum +=1;
				}
			}
			for(int pp=0;pp<termsDesc.size();pp++){
				if(termsDesc.get(pp).contains(this.DU)){
					this.ContSum +=1;				
				}
			}
		}
        double dd = (double)this.ContSum/this.N;
        this.RC.put(this.DU, dd);
        
        try
        {
            for(int kk=0; kk<this.termSet.size();kk++)
            {  
                String TLower = this.termSet.get(kk).toLowerCase();
                this.ContSum = 0.0;
                double RQRNumerator = 0.0;
                double RrankNumerator = 0.0;
                double RrankDenominator = 0.0;
                double TDNumeratorSnippet = 0.0;
                double TDNumeratorTitle = 0.0;
                
                int position=0;
                
                for (Entry<ArrayList, ArrayList> entry1 : this.SRRList.entrySet()) 
                {
        			ArrayList<String> termsTitle1 = entry1.getKey();
        			ArrayList<String> termsDesc1 = entry1.getValue();
        			
                    if((termsTitle1.contains(TLower))|| (termsDesc1.contains(TLower)))
                    {
                        this.ContSum += 1;
                        String[] TUTerms = this.TU.split("\\s+");;
                        ArrayList<Double>intersectCount = new ArrayList<Double>();
                        for(int tt=0;tt<TUTerms.length; tt++)
                        {
                        	intersectCount.add((double) (Collections.frequency(termsTitle1, TUTerms[tt])+Collections.frequency(termsDesc1, TUTerms[tt])));                   
                        }
                        int indx = intersectCount.indexOf(Collections.min(intersectCount));
                        double intersect = intersectCount.get(indx);
                        RQRNumerator += (double)intersect/this.statement.split("\\s+").length;
                        RrankNumerator += (double)(1 - (position+1.1)/this.N);
                    }
                    RrankDenominator += (double)(1 - (position+1.1)/this.N);
                   // """ TD """
                   if(termsDesc1.contains(TLower)){
                	   
                        int Snippet_len = termsDesc1.size();
                        
                        int min_winsize = this._findMinWinSize(termsDesc1, TLower);
                        int intersect = Collections.frequency(termsDesc1, this.TU);
                        TDNumeratorSnippet += (double)(Snippet_len - min_winsize) * intersect;
                        TDNumeratorSnippet /= (double)this.statement.split("\\s+").length;
                       
                    }
                   
                    if(termsTitle1.contains(TLower))
                    {
                        int Snippet_len = termsTitle1.size();
                        int min_winsize = this._findMinWinSize(termsTitle1, TLower);
                        int intersect = Collections.frequency(termsTitle1, this.TU);
                        TDNumeratorTitle += (double)(Snippet_len - min_winsize) * intersect;
                        TDNumeratorTitle /= (double)this.statement.split("\\s").length;
                    }
                    
                    position +=1;
                    
                }
                if(this.ContSum != 0){
                    this.RC.put(TLower, (double) (this.ContSum/this.N));
                    
                    if(this.ContSum == 0){
                        this.RQR.put(TLower, (double) this.ContSum);
                    }
                    else{
                    	this.RQR.put(TLower, (double) (RQRNumerator/this.ContSum));
                    }
                    
                    this.Rrank.put(TLower, (double) (RrankNumerator/RrankDenominator));
                    this.TD.put(TLower, (double) ((TDNumeratorSnippet + TDNumeratorTitle)/(this.N)));
                    
                    double SC = 0;
                    this.SC.put(TLower, SC);
                    

                    double TLCNumerator = 0;
                    double TLCDenominator = 0;
                    double sT = 0;
                    double sDU = 0;
                    for (Entry<ArrayList, ArrayList> entry : this.SRRList.entrySet()) {
            			ArrayList<String> termsTitle = entry.getKey();
            			ArrayList<String> termsDesc = entry.getValue();
            			
                        double contT = 0;
                        double contDU = 0;
                        if((termsTitle.contains(TLower))|| (termsDesc.contains(TLower)))contT = 1;
                            
                        if((termsTitle.contains(this.DU))|| (termsDesc.contains(this.DU)))contDU = 1;
                            
                        TLCNumerator += (contT - this.RC.get(TLower))*(contDU - this.RC.get(this.DU));
                        sT += Math.pow(contT - this.RC.get(TLower), 2);
                        sDU += Math.pow(contDU - this.RC.get(this.DU), 2);
                    }
                    TLCDenominator = Math.sqrt(sT * sDU);
                    if(TLCDenominator == 0.0)TLCDenominator = 0.000001;
                    
                    this.TLC.put(TLower, (double)TLCNumerator/TLCDenominator);
                }
                else{
                    removeTermList.add(this.termSet.get(kk));
                    continue;
                }
            }   
            for(int pp=0;pp<removeTermList.size(); pp++)
                this.termSet.remove(removeTermList.get(pp));
        
        }catch(Exception e){
            e.printStackTrace();
    	}
    }

	
	int _findMinWinSize(ArrayList<String> termsDesc, String tLower) 
	{
		ArrayList<Integer>indexList = new ArrayList<Integer>();
		for(int i=0; i<termsDesc.size(); i++){
			String x = termsDesc.get(i);
			if(x.equalsIgnoreCase(this.TU)||x.equals(tLower)){
				indexList.add(new Integer(i));
			}
		}
		if(indexList.size()==0){
			return 0;
		}
		else
		{
			int obj_max = (int)Collections.max(indexList);
			int obj_min = (int)Collections.min(indexList);
			int min_size = obj_max - obj_min;
			return min_size;
		}
	}

	LinkedHashMap<String, Double>candidateTRank() throws UnsupportedEncodingException, FileNotFoundException, IOException
	{
		TreeMap<String, Double> sortedTRank = new TreeMap<String, Double>();
    	LinkedHashMap<String, Double> sortedTRank1 = new LinkedHashMap<String, Double>();
    	DecimalFormat f = new DecimalFormat("##.00");
    	double score;
        Iterator<String> itr = this.termSet.iterator();
        while(itr.hasNext())
        {       	
            String key = itr.next().toLowerCase();
            double w1=1.0, w2=1.0, w3=1.0, w4=1.0, w5=1.0, w6 =1.0; 
            score = 0.0;
            score += (double)w1*this.RC.get(key);
            score += (double)w2*this.RQR.get(key);
            score += (double)w3*this.Rrank.get(key);
            score += (double)w4*this.TD.get(key);
            score += (double)w5*this.SC.get(key);
            score += (double)w6*this.TLC.get(key);
            this.AlterRankScore.put(key, Double.parseDouble(f.format(score)));
        }      
        sortedTRank= SortByValue(this.AlterRankScore);  
        
        int top3 = 0;
        int len3 = sortedTRank.size();
        for(Map.Entry<String, Double> entry : sortedTRank.entrySet())
        {
        	  String key = entry.getKey();
        	  Double value = entry.getValue();
        	  sortedTRank1.put(key, value);                    
        	  top3++;
        	  if(len3>=3){
        		  if(top3==3)break;
        	  }
       	  
        }
        return sortedTRank1;
	}

	
	void filterDataType()
	{
		for (String firstName : this.POSDict.keySet()) 
		{
		     List<String> lastNames = (List<String>) this.POSDict.get(firstName);
		     if(lastNames.size()>0)
		     {
		    	 String mostcommon = mostCommonElement(lastNames);
		    	 if(mostcommon.equalsIgnoreCase(this.DUPOS))
		    	 	{
		    		 this.termSet.add(firstName);
		    	 	}
		     }
		 }
	}
	
	String mostCommonElement(List<String> list) {
	    Map<String, Integer> map = new HashMap<String, Integer>();
	     
	    for(int i=0; i< list.size(); i++) {
	         
	        Integer frequency = map.get(list.get(i));
	        if(frequency == null) {
	            map.put(list.get(i), 1);
	        } else {
	            map.put(list.get(i), frequency+1);
	        }
	    }
	     
	    String mostCommonKey = null;
	    int maxValue = -1;
	    for(Map.Entry<String, Integer> entry: map.entrySet()) {
	         
	        if(entry.getValue() > maxValue) {
	            mostCommonKey = entry.getKey();
	            maxValue = entry.getValue();
	        }
	    }
	     
	    return mostCommonKey;
	}
	
	//decide part of speech tagging of each sentence
	POSNERterm _getTagsStatementKorean(String statement1) throws Exception {
		ArrayList<String>POSStatement = new ArrayList<String>();
		LinkedHashMap<String, String>POSDict = new LinkedHashMap<String, String>();
		
		statement1 = statement1.replaceAll("[^a-zA-Z0-9\uAC00-\uD7AF\u1100-\u11FF\u3130-\u318F ]"," ");
		
		String a[] = statement1.split("\\s+");
		String statement = "";
		for (int i = 0; i < a.length; i++) {
			statement = statement + a[i]+" ";
		}
		
		List<MExpression> ret = this.ma.analyze(statement);
		ret = this.ma.postProcess(ret);
		ret = this.ma.leaveJustBest(ret);
		List<Sentence> stl = this.ma.divideToSentences(ret);

		for( int i = 0; i < stl.size(); i++ ) {
			Sentence st = (Sentence) stl.get(i);
			for( int j = 0; j < st.size(); j++ ) {
				String[] tags = st.get(j).toString().trim().replaceAll(".*\\[", "").replaceAll("\\]", "").split("\\+");
				for (String tag : tags) {
					String[] s = tag.split("/");
					POSDict.put(s[1], s[2]);
				}
			}
		}

		ma.closeLogger();
		
	   	Set<Entry<String, String>> set = POSDict.entrySet();
	   	Iterator<Entry<String, String>> it = set.iterator();
	   	while(it.hasNext())
	   	 {
	   		Map.Entry me = (Map.Entry)it.next();
	   		POSStatement.add(me.getKey().toString());
	   	}
	           
	       POSNERterm pOSNERterm1 = new POSNERterm();
	       pOSNERterm1.setPOSDict(POSDict);
	       pOSNERterm1.settermList(POSStatement);
	       return pOSNERterm1;
	}
	
	String _getTU() 
	{ 
		if((this.DU.length())!=0)
		{
			int lIndex = this.statement.indexOf(this.DU);
			int rIndex = lIndex + this.DU.length();
	        String p1 = this.statement.substring(0, lIndex).trim();
	        String p2 = this.statement.substring(rIndex).trim();
	        return p1 + " " + p2;
	    }
	    return "";
	}

	//sorting each alternatives for doubtful unit in order of increasing their trustscore
	public static TreeMap<String, Double> SortByValue 
				(HashMap<String, Double> map) {
		ValueComparator vc =  new ValueComparator(map);
		TreeMap<String,Double> sortedMap = new TreeMap<String,Double>(vc);
		sortedMap.putAll(map);
		return sortedMap;
	}		
}

class ValueComparator implements Comparator<String> {
	 
    Map<String, Double> map;
 
    public ValueComparator(HashMap<String,Double> map2) {
        this.map = map2;
    }
 
    public int compare(String a, String b) {
        if (map.get(a) >= map.get(b)) {
            return -1;
        } else {
            return 1;
        } 
    }
}

class POSNERterm
{
	LinkedHashMap<String, String> POSDict5 = new LinkedHashMap<String, String>();
	ArrayList<String> termList5 = new ArrayList<String>();	
	
	void setPOSDict(LinkedHashMap<String, String> POSDict)
	{
		this.POSDict5=POSDict;
	}
	void settermList(ArrayList<String> termList)
	{
		this.termList5 = termList;
	}
	LinkedHashMap<String, String> getPOSDict()
	{
		return POSDict5;
	}
	ArrayList<String> gettermList()
	{
		return termList5;
	}
}