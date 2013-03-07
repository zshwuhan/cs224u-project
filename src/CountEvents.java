import java.util.*;

import edu.jhu.agiga.AgigaCoref;
import edu.jhu.agiga.AgigaDocument;
import edu.jhu.agiga.AgigaMention;
import edu.jhu.agiga.AgigaPrefs;
import edu.jhu.agiga.AgigaSentence;
import edu.jhu.agiga.AgigaToken;
import edu.jhu.agiga.AgigaTypedDependency;
import edu.jhu.agiga.BasicAgigaSentence;
import edu.jhu.agiga.StanfordAgigaSentence;
import edu.jhu.agiga.StreamingDocumentReader;
import edu.jhu.agiga.StreamingSentenceReader;
import edu.jhu.agiga.AgigaConstants.DependencyForm;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

public class CountEvents {
	public static final double BETA = 0.1;
	public static final double LAMBDA = 0.1;
	
	
	// Issue; we probably can't store everything.
	// And this way may just take the firstN, rather than the bestN
	public static final int MAX_VERBS = 20;
	public static final int MAX_EVENTS = MAX_VERBS * 2;
	public static final int MAX_PROTAGONISTS = 30;
	public static final int DEFAULT_VERB = 0;
	public static final int DEFAULT_PRO = 0;
	public static final int NONE_PRO = 1;
	
	public int curVerbs = 0;
	public int curPros = 0;
	public int mentionsMissedCount = 0;
	public int mentionsHitCount =0;
	public int totMentions =0;

	
	//public String[] verbs = new String[MAX_VERBS]; // printable verb, CountEvents.verbs[Event.verb]
	//public String[] pros = new String[MAX_PROTAGONISTS];
	
	// MOSTLY FOR JUST HELPING US SET MAX_VERBS AND MAX_PROTAGONISTS, UNLIMITED!
	//public Set<String> verbsList = new HashSet<String>();
	//public Set<String> prosList = new HashSet<String>();
	
	// List of events and protagonists 
	public List<Event> eventList = new ArrayList<Event>();
	public List<Protagonist> protList = new ArrayList<Protagonist>();
	
	// go from verb/protagonist to int. IS LIMITED BY MAX_VERBS AND MAX_PROTAGONISTS
	public Map<Pair<String,String>, Integer> verbArgTypeMap = new HashMap<Pair<String,String>, Integer>();
	public Map<String, Integer> prosMap = new HashMap<String, Integer>();
	
	public Map<Pair<Event, Event>, Integer> eventPairCounts = new HashMap<Pair<Event, Event>, Integer>();
	public int eventPairOverallCount = 0;
	
	public Map<Triple<Event, Event, Protagonist>, Integer> eventPairProCounts =
		new HashMap<Triple<Event, Event, Protagonist>, Integer>();
	
	// mapping from Event to its index in the count array below
	public Map<Event, Integer> eventsCountMap = new HashMap<Event, Integer>();
	
	public Map<Event,String> eventToVerbMap = new HashMap<Event, String>();
	//public int[] eventCounts = new int[MAX_EVENTS]; // redundant, but useful
	//public List<Integer> eventCounts = new ArrayList<Integer>();
	public int eventOverallCount = 0;
	
	public CountEvents() {
		initialize();
	}
	
	public CountEvents(String filename) {
		// I would load the data in here directly instead
	}
	
	private void initialize() {
		curVerbs = 1;
		curPros = 2;
		//verbs[DEFAULT_VERB] = "<VERB>";
		//pros[DEFAULT_PRO] = "<PRO>";
		//pros[NONE_PRO] = "<NONE>";
	}
	
	public void printCounts(){
		
		System.out.println("The total event count is " + eventOverallCount);
		
		for(Event e :eventsCountMap.keySet()){
			System.out.println(eventToVerbMap.get(e)+ " " + e.argType + " => " + eventsCountMap.get(e));
		}
		
		System.out.println("Mentions missed count "+ mentionsMissedCount);
		System.out.println("Mentions hit count " + mentionsHitCount);
		System.out.println("Total mentions are " + totMentions);
	}
	
	public void printMentionMap(Map<Pair<Integer,Integer>,Integer> mentionMap){
		
		
		for(Pair<Integer,Integer> p :mentionMap.keySet()){
			System.out.println(p.x+ " " +p.y + " => " + mentionMap.get(p));
		}
		
	}	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Let's ensure that we have default events and default protagonists, in case we overflow
		
		CountEvents countEvents = new CountEvents();
		Event.countEvents = countEvents;
		EventChain.countEvents = countEvents;
		NarrativeSchema.countEvents = countEvents;
		Protagonist.countEvents = countEvents;
		
		//getCountsByDeps(countEvents);
		getCountsByMentions(countEvents);
		
	}
	
	
	public static void getCountsByMentions(CountEvents countEvents){
		
		// Here we try to get the counts by looking at the mentions first
		
		// for each document
			//find all the dependencies and create a map
			// find all the mentions
				// for a set of coreferent mentions. 
					// find the dependencies involving the head tokens of each mention and create an event for each(if not alread present). increment the event counts and alleventcount. Also maintain a separate list of all events that we found. In this list we repeat events. That is if mention 1 and mention 5 are dependent on same verb, two events appear in the list. Just so the counts get repeated
			//create a protagonist(if it does not already exist) for the lemma of the most frequent headtoken
			// for all pairs of the above found events, increment the event-event counts. Also increment the event-event-protagonist counts
		
		String[] files = new String[1];
		files[0] = "C:\\Users\\aman313\\Documents\\Winter-2013\\cs224u\\agiga_1.0\\Data\\Set1\\afp_eng_199405.xml.gz";
		for(String file:files){
	        AgigaPrefs prefs = new AgigaPrefs();
	        prefs.setAll(false);
	        prefs.setWord(true);
	        prefs.setCoref(true);
	        prefs.setLemma(true);
	        prefs.setDeps(DependencyForm.BASIC_DEPS);
	        int indx =0;
	        StreamingDocumentReader reader = new StreamingDocumentReader(file, prefs);
	        int idx=0;
	        
	        for(AgigaDocument doc:reader){
	        	if(idx==10)break;
	        	// get all mentions for the document
	        	doc.assignMucStyleIdsAndRefsToMentions(); // is this needed ??
	        	List<AgigaCoref> corefs = doc.getCorefs();
	        	List<AgigaSentence> sents = doc.getSents();
	        
	        	Map<String,AgigaTypedDependency> depsMap = new HashMap<String, AgigaTypedDependency>();
	        	for(AgigaSentence sent :sents){
	        		List<AgigaToken> tokens = sent.getTokens();
	        		List<AgigaTypedDependency> deps = sent.getAgigaDeps(DependencyForm.BASIC_DEPS);
	        		for(AgigaTypedDependency dep :deps){
	        			if(dep.getType().equals("nsubj") || dep.getType().equals("dobj")){
	        				//AgigaToken tokGov = tokens.get(dep.getGovIdx());//Verb token
	        				AgigaToken tokDep = tokens.get(dep.getDepIdx());
	        				depsMap.put(sent.getSentIdx()+ " " +tokDep.getTokIdx() , dep);
	        			}
	        		} 
	        	
	        	}// dependency map created
	        	System.out.println("Created Dependency Map");
	        	
	        	for(AgigaCoref coref:corefs){
	        	
	        		List<AgigaMention> mentions = coref.getMentions();
	        		Map<String,Integer> lemmaCount = new HashMap<String, Integer>();
	        		List<Event> events = new ArrayList<Event>();
	 				Protagonist p = null;
	        		for(AgigaMention mention: mentions){	
	        			countEvents.totMentions++;
	        			AgigaSentence sent = sents.get(mention.getSentenceIdx());// Get the sentence
	        			List<AgigaToken> tokens = sent.getTokens();
	        			AgigaToken token = tokens.get(mention.getHeadTokenIdx());
	        			String lemma = token.getLemma();
	        			Integer count = lemmaCount.get(lemma);
	        			if(count == null) {
	        				lemmaCount.put(lemma, 1);
	        			}else{ 
	        				lemmaCount.put(lemma, count.intValue()+1);
	        			}// We will need the lemma count for the protagonist
	        			
	        			//check if the mention is there in the dependency map
	        			AgigaToken headToken = tokens.get(mention.getHeadTokenIdx());// This is not really needed
	        			AgigaTypedDependency depend = depsMap.get(mention.getSentenceIdx()+ " "+mention.getHeadTokenIdx());
	   
	        			if(depend!=null){
	        				countEvents.mentionsHitCount++;
	        				AgigaToken tokGov = tokens.get(depend.getGovIdx());//Verb token
	        				AgigaToken tokDep = tokens.get(depend.getDepIdx());
	        				String type = depend.getType();
	        				Boolean typ = type.equals("nsubj")?true:false;
	        				Integer temp = countEvents.verbArgTypeMap.get(new Pair<String,String>(tokGov.getLemma(),type));	  
	        				
	        				int eventIndex = -1;
	        				if (temp != null)
	        					eventIndex = temp.intValue();
	        				Event e;
	        				if(eventIndex>=0){
	        					e=countEvents.eventList.get(eventIndex);
	        						
	        				}else{
	        					eventIndex = countEvents.eventList.size();
	        					
	        					e= new Event(eventIndex,typ);	// create a new event object	
	        					e.argTokId = tokDep.getTokIdx();
	        					e.sentId = sent.getSentIdx();
	        					// update countevents and verbargmap
	        					countEvents.eventList.add(e);
	        					countEvents.verbArgTypeMap.put(new Pair<String,String>(tokGov.getLemma(),type), eventIndex);

	        					// first time we've seen this verb, so add a new entry in the reverse map
	        					countEvents.eventToVerbMap.put(e, tokGov.getLemma());
	        				}

	        				events.add(e);
        					int currCnt = countEvents.eventsCountMap.get(e)!=null?countEvents.eventsCountMap.get(e):0;
        					countEvents.eventsCountMap.put(e, currCnt+1);
        					// increment overall event counter
        					countEvents.eventOverallCount++;

	        				
	        			}else{
	        				
	        				// print the headmention word and the mentions of this sentence 
	        				System.out.println("-----------------------------------------------------------------------------------------------------------------------------");
	        				System.out.println("HEADWORD:" + headToken.getWord()+ " "+ mention.getSentenceIdx()+" "+headToken.getTokIdx());
	        				AgigaSentence thisSent = sents.get(mention.getSentenceIdx());
	        				List<AgigaTypedDependency> deps = thisSent.getAgigaDeps(DependencyForm.BASIC_DEPS);
	        				for(AgigaTypedDependency dep:deps){
	        					AgigaToken tokDep = tokens.get(dep.getDepIdx());
	        					if(tokDep.getTokIdx() == headToken.getTokIdx() && (dep.getType().equals("nsubj") ||dep.getType().equals("dobj") ) ){
	    	        				countEvents.mentionsMissedCount++;
	        						System.out.println(tokDep.getWord()+  " " + thisSent.getSentIdx()+ " "+tokDep.getCharOffBegin()+" "+dep.getType()+ " "+tokDep.getTokIdx());
	        					}
	        				
	        				}
	        				
	        				System.out.println("--------------------------------------------------------------------------------------------------------------------------------");
	        			}
	        			
	        		} // seen all coreferent mentions in this chain and have created a list of events. Also have updated individual event counts and overallcount
	        		
	        		// Now create/find the protagonist
	        		int max =-1;
	        		String protLemma = "";
	        		for(String lemma:lemmaCount.keySet()){
	        			if(lemmaCount.get(lemma) >= max){
	        				protLemma = lemma;
	        				max = lemmaCount.get(lemma);
	        			}
	        		}

   
	        		if(max > 0){ // Mentions list is not empty. Which it reallly should not be. But who the hell knows anything anymore!!
        				int protIndex = countEvents.prosMap.get(protLemma)!=null?countEvents.prosMap.get(protLemma):-1;
        				if(protIndex >=0 ){
        					p = countEvents.protList.get(protIndex);
        				}else{// make a new protagonist if not present
        					protIndex = countEvents.protList.size();
        					p = new Protagonist(protIndex);
        					countEvents.protList.add(p);
        					countEvents.prosMap.put(protLemma, protIndex);
        					
        				}
	        			
	        		}// okay. done with all that. Now to pair counts and stuff
	        		
	        		for(Event e1:events){
	        			for(Event e2:events){
	        				if(e1.equals(e2)) continue;
	        				Integer temp = countEvents.eventPairCounts.get(new Pair<Event,Event>(e1,e2));
	        				int currCount;
	        				if(temp!=null){
	        					currCount = temp.intValue();
	        				}else{
	        					currCount =0;
	        				}
    						countEvents.eventPairCounts.put(new Pair<Event,Event>(e1,e2),currCount+1 ); //  order of the pair does not matter. The hashcode should reflect this I guess. if we want the order to matter we should follow some convention like <oldevent,newevent>	        						
    				
    						if(p!=null){// should not happen really
    							temp = countEvents.eventPairProCounts.get(new Triple<Event,Event,Protagonist>(e1,e2,p));
    							if(temp!=null){
    								currCount = temp.intValue();
    							}else{
    								currCount=0;
    							}
    							countEvents.eventPairProCounts.put(new Triple<Event,Event,Protagonist>(e1,e2,p), currCount+1);
    						}
	        				
	        			}
	        			
	        		}
	        		
	        		
	        		
	        	}// dealt with the corefs in this documents
	        	idx++;	
	        }//document dealt with
	        			
		} // all files read
		countEvents.printCounts();
		
	}
	
	
	
	public static void getCountsByDeps(CountEvents countEvents){
		
		// for (each document)
		// for (each sentence that we see)
		// take the subject verb object
		// It's possible that there isn't a subject/object, so use NONE
		// Anyway, if there's still space, add on to the current list of verbs and pros
		// And add on to the counts properly
		
		
		String[] files = new String[1];
		files[0] = "C:\\Users\\aman313\\Documents\\Winter-2013\\cs224u\\agiga_1.0\\Data\\Set1\\afp_eng_199405.xml.gz";
	
		for(String file: files){
	        AgigaPrefs prefs = new AgigaPrefs();
	        prefs.setAll(false);
	        prefs.setWord(true);
	        prefs.setCoref(true);
	        prefs.setLemma(true);
	        //prefs.setPos(true);
	        prefs.setDeps(DependencyForm.BASIC_DEPS);
	        //prefs.setDeps(DependencyForm.COL_DEPS);
	        //prefs.setDeps(DependencyForm.COL_CCPROC_DEPS);
	        int indx =0;
	        StreamingDocumentReader reader = new StreamingDocumentReader(file, prefs);
	        //StreamingSentenceReader readerSent  = new StreamingSentenceReader(file, prefs);
	        //List<Event> eventsSeen = new ArrayList<Event>();
	        //HashMap<Pair<String,String>,Event> eventsMap = new HashMap<Pair<String,String>,Event>(); // Map to find if an event slot has been seen before
	         // We 
	        for (AgigaDocument doc : reader) {
	        	if(indx == 10) break;
	        	doc.assignMucStyleIdsAndRefsToMentions();
	        	HashMap<Pair<Integer,Integer>,Integer> mentionMap = new HashMap<Pair<Integer,Integer>,Integer>();
	        	List<AgigaSentence> sents= doc.getSents();
	        	List<AgigaCoref> corefs  = doc.getCorefs();
	        	//Add the corefs to the map
	        	int corefId =1;
	        	for(AgigaCoref coref : corefs){
	        		List<AgigaMention> mentions = coref.getMentions();
	        		int id = mentions.get(0).getMucRef();
	        		
	        		for(AgigaMention mention:mentions){
	        			if(mention.getMucRef()!=id){
	        				//System.out.println("ALERT!!!!!!!!!!!");
	        			}
	        			//mentionMap.put(new Pair<Integer,Integer>(mention.getSentenceIdx(),mention.getStartTokenIdx()), corefId);
	        			AgigaSentence sent = doc.getSents().get(mention.getSentenceIdx());
	        			AgigaToken tok = sent.getTokens().get(mention.getHeadTokenIdx());
	        			int startPOs= tok.getCharOffBegin();
	        			mentionMap.put(new Pair<Integer,Integer>(mention.getSentenceIdx(),mention.getHeadTokenIdx()), corefId);
	        			//AgigaToken tokS = sent.getTokens().get(mention.getStartTokenIdx());
	        			//AgigaToken tokE = sent.getTokens().get(mention.getEndTokenIdx() -1);
	        			//System.out.println( tokS.getWord()+" " + tok.getWord()+ " "+ tokE.getWord());
	        		}
	        		corefId++;
	        	}
	        	
	        	Map<Event, Set<Integer>> eventToMentionsMap = new HashMap<Event, Set<Integer>>();
	        	System.out.println("Document " + doc);

	        	//System.out.println(corefs.);
	        	for(AgigaSentence sent :  sents){
	        		
	        		List<AgigaToken> tokens =sent.getTokens();	        		
	        		List<AgigaTypedDependency> deps =sent.getAgigaDeps(DependencyForm.BASIC_DEPS);
	        		for(AgigaTypedDependency dep:deps){
	        			//System.out.println(dep.getType());
	        			if(dep.getType().equals("nsubj") ){ // found an event-subj pair
	        				AgigaToken tokDep = tokens.get(dep.getDepIdx());//subject
	        				AgigaToken tokGov = tokens.get(dep.getGovIdx()); //verb
	        				//System.out.println(tokGov.getWord()+" "+tokGov.getLemma()+  " "+ tokGov.getPosTag() );
	        				
	        				// first get the event object
	        				Integer temp = countEvents.verbArgTypeMap.get(new Pair<String,String>(tokGov.getLemma(),"nsubj"));
	        				
	        				int eventIndex = -1;
	        				if (temp != null)
	        					eventIndex = temp.intValue();
	        				Event e;
	        				if(eventIndex>=0){
	        					e=countEvents.eventList.get(eventIndex);
	        					
	        					
	        				}else{
	        					eventIndex = countEvents.eventList.size();
	        					e= new Event(eventIndex,true);	// create a new event object	
	        					//e.docId = doc.getDocId();
	        					e.argTokId = tokDep.getTokIdx();
	        					e.sentId = sent.getSentIdx();
	        					// update countevents and verbargmap
	        					countEvents.eventList.add(e);
	        					countEvents.verbArgTypeMap.put(new Pair<String,String>(tokGov.getLemma(),"nsubj"), eventIndex);

	        					// first time we've seen this verb, so add a new entry in the reverse map
	        					countEvents.eventToVerbMap.put(e, tokGov.getLemma());
	        				}
	        				
        					// here, we look up the current mention and make sure our event knows its associated with it
        					Set<Integer> mentions = eventToMentionsMap.get(e);
        					if (mentions == null)
        						mentions = new HashSet<Integer>();
        					mentions.add(mentionMap.get(new Pair<Integer, Integer>(sent.getSentIdx(), tokDep.getTokIdx())));
        					eventToMentionsMap.put(e,  mentions);
	        				//if(eventToMentionsMap.get(e)==null){
	        					//System.out.println("Here");
	        				//}
        					// also increment the event count
        					int currCnt = countEvents.eventsCountMap.get(e)!=null?countEvents.eventsCountMap.get(e):0;
        					countEvents.eventsCountMap.put(e, currCnt+1);
        					// increment overall event counter
        					countEvents.eventOverallCount++;
	        				
	        				// We now get the protagonist object

	        				int protIndex = countEvents.prosMap.get(tokDep.getLemma())!=null?countEvents.prosMap.get(tokDep.getLemma()):-1;
	        				Protagonist p;
	        				if(protIndex >=0 ){
	        					p = countEvents.protList.get(protIndex);
	        				}else{// make a new protagonist if not present
	        					protIndex = countEvents.protList.size();
	        					p = new Protagonist(protIndex);
	        					countEvents.protList.add(p);
	        					countEvents.prosMap.put(tokDep.getLemma(), protIndex);
	        					
	        				}
	        				
	        				// Now look at all the events seen check if the protagonists are coreferent
	        				for(int index = 0; index < countEvents.eventList.size(); index++){
	        					Event e1 = countEvents.eventList.get(index);
	        					if(e1.equals(e))continue; // equals might need to be overridden
	        				//	AgigaMention m1 = mentionMap.get(new Pair<Integer,Integer>(e1.sentId,e1.argTokId));     					
	        					//AgigaMention m2 = mentionMap.get(new Pair<Integer,Integer>(e.sentId,e.argTokId));
	        					if(eventToMentionsMap.get(e1)==null){
	        						continue;
	        					}
	        					for (Integer m1 : eventToMentionsMap.get(e1)) {	        					
		        					//Integer m1 = mentionMap.get(new Pair<Integer,Integer>(e1.sentId,e1.argTokId));     					
		        					Integer m2 = mentionMap.get(new Pair<Integer,Integer>(e.sentId,e.argTokId));
	
		        					if(m1==null){
	
		        						//System.out.println("m1 was null");
		        						//countEvents.mentionsMissedCount++;
		        						continue;
		        					}
		        					if(m2==null){
		        						//System.out.print("m2 was null ");
		        						System.out.println(tokDep.getWord());
		        						countEvents.mentionsMissedCount++;
		        						continue;
		        					}
		        					
		        					if(/*m1!=null && m2!=null &&*/m1.intValue() == m2.intValue()){// coreferent(I presume!!)
		        						// Time to increment count. Finally!!
		        						countEvents.mentionsHitCount++;
		        						int currCount = (countEvents.eventPairCounts.get(new Pair<Event,Event>(e1,e))!=null)?countEvents.eventPairCounts.get(new Pair<Event,Event>(e1,e)):0;
		        						countEvents.eventPairCounts.put(new Pair<Event,Event>(e1,e),currCount+1 ); //  order of the pair does not matter. The hashcode should reflect this I guess. if we want the order to matter we should follow some convention like <oldevent,newevent>	        						
		        				
		        						currCount = countEvents.eventPairProCounts.get(new Triple<Event,Event,Protagonist>(e1,e,p))!=null?countEvents.eventPairProCounts.get(new Triple<Event,Event,Protagonist>(e1,e,p)):0;
		        						countEvents.eventPairProCounts.put(new Triple<Event,Event,Protagonist>(e1,e,p), currCount+1);
		        					}
	        					}
	        				}    				
	        			} // subject dealt with. do the same for object
	        			
	        			
	        		}
	        		
	        		
	        	
	        	}
	        	indx++;
	        	countEvents.printMentionMap(mentionMap);
	        }// done with all documents in this file	        
	        
			countEvents.printCounts();
		}// done with all files
		

		
	}

}
