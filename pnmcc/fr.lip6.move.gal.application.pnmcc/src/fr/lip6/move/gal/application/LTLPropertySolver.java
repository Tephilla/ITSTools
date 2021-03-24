package fr.lip6.move.gal.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import android.util.SparseIntArray;
import fr.lip6.ltl.tgba.AcceptedRunFoundException;
import fr.lip6.ltl.tgba.EmptyProductException;
import fr.lip6.ltl.tgba.LTLException;
import fr.lip6.ltl.tgba.RandomProductWalker;
import fr.lip6.ltl.tgba.TGBA;
import fr.lip6.move.gal.mcc.properties.DoneProperties;
import fr.lip6.move.gal.structural.DeadlockFound;
import fr.lip6.move.gal.structural.GlobalPropertySolvedException;
import fr.lip6.move.gal.structural.ISparsePetriNet;
import fr.lip6.move.gal.structural.SparsePetriNet;
import fr.lip6.move.gal.structural.StructuralReduction;
import fr.lip6.move.gal.structural.StructuralReduction.ReductionType;
import fr.lip6.move.gal.structural.expr.AtomicProp;
import fr.lip6.move.gal.structural.expr.Expression;
import fr.lip6.move.gal.structural.expr.Op;
import fr.lip6.move.gal.structural.smt.DeadlockTester;

public class LTLPropertySolver {

	private String spotPath;
	private String solverPath;
	private String workDir;
	private boolean exportLTL;

	public LTLPropertySolver(String spotPath, String solverPath, String workDir, boolean exportLTL) {
		this.spotPath = spotPath;
		this.solverPath = solverPath;
		this.workDir = workDir;
		this.exportLTL = exportLTL;
	}

	public int runStructuralLTLCheck(MccTranslator reader, boolean isSafe, DoneProperties doneProps)
			throws IOException, TimeoutException, LTLException {
		int solved =0;
		if (reader.getHLPN() != null) {
			if (exportLTL) {					
				SpotRunner.exportLTLProperties(reader.getHLPN(),"col",workDir);
			}
			solved += ReachabilitySolver.checkInInitial(reader.getHLPN(),doneProps);
			SpotRunner sr = new SpotRunner(spotPath, workDir, 10);
			sr.runLTLSimplifications(reader.getHLPN());
			if (exportLTL) {					
				SpotRunner.exportLTLProperties(reader.getHLPN(),"colred",workDir);
			}
		}
		reader.createSPN();
		solved += ReachabilitySolver.checkInInitial(reader.getSPN(),doneProps);
		if (exportLTL) {
			SpotRunner.exportLTLProperties(reader.getSPN(),"raw",workDir);
		}
	
		if (spotPath != null) {
			SpotRunner sr = new SpotRunner(spotPath, workDir, 10);
			sr.runLTLSimplifications(reader.getSPN());
		}
		
		solved += ReachabilitySolver.checkInInitial(reader.getSPN(),doneProps);
		
		if (reader.getSPN().getProperties().isEmpty()) {
			System.out.println("All properties solved without resorting to model-checking.");
			return solved;
		}
		
		solved += new AtomicReducerSR().strongReductions(solverPath, reader, isSafe, doneProps);
		solved += ReachabilitySolver.checkInInitial(reader.getSPN(),doneProps);
	
		solved += GALSolver.runGALReductions(reader, isSafe, doneProps);
		
		reader.getSPN().getProperties().removeIf(p -> doneProps.containsKey(p.getName()));
		if (spotPath != null) {
			SpotRunner sr = new SpotRunner(spotPath, workDir, 10);
			sr.runLTLSimplifications(reader.getSPN());
		}
		runStutteringLTLTest(reader, doneProps, isSafe);
			
		reader.getSPN().getProperties().removeIf(p -> doneProps.containsKey(p.getName()));
		return solved;
	}

	public void runStutteringLTLTest(MccTranslator reader, DoneProperties doneProps, boolean isSafe)
			throws TimeoutException, LTLException {
		SpotRunner spot = new SpotRunner(spotPath, workDir, 10);
		
		
		
		for (fr.lip6.move.gal.structural.Property propPN : reader.getSPN().getProperties()) {
			if (doneProps.containsKey(propPN.getName())) 
				continue;
			
			TGBA tgba = spot.transformToTGBA(propPN);
			if (tgba.getProperties().contains("stutter-invariant")) {
				
				// build a new copy of the model, with only this property				
				SparsePetriNet spn = new SparsePetriNet(reader.getSPN());
				spn.getProperties().clear();
				spn.getProperties().add(propPN.copy());
				
				// ok let's reduce the system for this property 
				StructuralReduction sr = new StructuralReduction(spn);
				BitSet support = spn.computeSupport();
				System.out.println("Support contains "+support.cardinality() + " out of " + sr.getPnames().size() + " places. Attempting structural reductions.");
				sr.setProtected(support);
				try {
					sr.reduce(ReductionType.SI_LTL);
				} catch (GlobalPropertySolvedException gse) {
					System.out.println("Unexpected exception when reducting for LTL :" +gse.getMessage());
				}
				// rebuild and reinterpret the reduced net
				spn.readFrom(sr);
				// we can maybe simplify some predicates now : apply some basic tests
				spn.testInInitial();
				spn.removeConstantPlaces();
				spn.simplifyLogic();

				// index of places may have changed, formula might be syntactically simpler 
				// recompute fresh tgba with correctly indexed AP
				tgba = spot.transformToTGBA(spn.getProperties().get(0));
				// annotate it with Infinite Stutter Acceped Formulas
				spot.computeInfStutter(tgba);

				// walk the product a bit
				RandomProductWalker pw = new RandomProductWalker(spn);

				try {
					System.out.println("Running random walk in product with property : " + propPN.getName() + " automaton " + tgba);
					pw.runProduct(tgba , 10000, 10);
				
					// so we couldn't find a counter example, let's reflect upon this fact.
					TGBA tgbak = applyKnowledgeBasedReductions(spn,tgba, isSafe);
					
					pw.runProduct(tgbak , 10000, 10);
				
				} catch (AcceptedRunFoundException a) {
					doneProps.put(propPN.getName(), false, "STUTTER_TEST");
				} catch (EmptyProductException e2) {
					doneProps.put(propPN.getName(), true, "STRUCTURAL INITIAL_STATE");
				}

				
				
				
			} else {
				spot.computeInfStutter(tgba);
				RandomProductWalker pw = new RandomProductWalker(reader.getSPN());
				
				try {
					System.out.println("Running random walk in product with property : " + propPN.getName() + " automaton " + tgba);
					pw.runProduct(tgba , 10000, 10);
					TGBA tgbak = applyKnowledgeBasedReductions(reader.getSPN(),tgba, isSafe);
					
					pw.runProduct(tgbak , 10000, 10);

					
				} catch (AcceptedRunFoundException a) {
					doneProps.put(propPN.getName(), false, "STUTTER_TEST");
				} catch (EmptyProductException e2) {
					doneProps.put(propPN.getName(), true, "STRUCTURAL INITIAL_STATE");
				}
				
				
			}
			
		}
	}

	private TGBA applyKnowledgeBasedReductions(ISparsePetriNet spn, TGBA tgba, boolean isSafe) {
		
		// cheap knowledge 
		List<Expression> knowledge = new ArrayList<>(); 
		
		addConvergenceKnowledge(knowledge, spn, tgba, isSafe);
		
		addInitialStateKnowledge(knowledge, spn, tgba);
		
		System.out.println("Knowledge obtained : " + knowledge);

		// try to reduce the tgba using this knowledge
		SpotRunner sr = new SpotRunner(spotPath, workDir, 10);

		
		for (Expression factoid : knowledge) {
			String ltl = sr.printLTLProperty(factoid);
			TGBA prod = sr.computeProduct(tgba, ltl);
			if (prod.getEdges().get(prod.getInitial()).size() == 0) {
				// this is just false !
				System.out.println("Property proved to be true thanks to knowledge :" + factoid);
				return prod;
			} else if (prod.getProperties().contains("stutter-invariant") && ! tgba.getProperties().contains("stutter-invariant")) {
				System.out.println("Adopting stutter invariant property thanks to knowledge :" + factoid);
				tgba = prod;
			} else if (prod.getAPs().size() < tgba.getAPs().size()) {
				System.out.println("Adopting property with smaller alphabet thanks to knowledge :" + factoid);
				tgba = prod;
			}
		}						

		return tgba;
		
	}

	private void addInitialStateKnowledge(List<Expression> knowledge, ISparsePetriNet spn, TGBA tgba) {
		SparseIntArray init = new SparseIntArray(spn.getMarks());
		for (AtomicProp ap : tgba.getAPs()) {
			if (ap.getExpression().eval(init) == 1) {
				knowledge.add(Expression.apRef(ap));
			} else {
				knowledge.add(Expression.not(Expression.apRef(ap)));
			}
		}
 	}

	private void addConvergenceKnowledge(List<Expression> knowledge, ISparsePetriNet spn, TGBA tgba, boolean isSafe) {
		// we are SCC free hence structurally we will meet a deadlock in all traces
		// hence we must be accepted in one of these states, and they are by definition stuttering
		boolean allPathsAreDead = testAFDead (spn);
		
		if (allPathsAreDead) {
			System.out.println("Detected that all paths lead to deadlock. Applying this knowledge to assert that all AP eventually converge : F ( (Ga|G!a) & (Gb|G!b)...)");
			
			boolean [] results = DeadlockTester.testAPInDeadlocksWithSMT(spn, tgba.getAPs(), solverPath, isSafe);						
			
			// build expressions : G p | G !p 
			// for each ap "p", but remove bad values eliminated through SMT
			for (int i=0,ie=tgba.getAPs().size() ; i < ie ; i++) {
				boolean posExist = results[i];
				boolean negExist = results[i+1];
				knowledge.add(
						Expression.op(Op.F, 
						Expression.op(Op.OR, 
						posExist ? Expression.op(Op.G, Expression.apRef(tgba.getAPs().get(i)), null): Expression.constant(false), 
						negExist ? Expression.op(Op.G, Expression.not(Expression.apRef(tgba.getAPs().get(i))),null): Expression.constant(false)),null));
				if (!posExist && ! negExist) {
					System.out.println("Strange error detected, AP can be neither true nor false in deadlock.");
				}
			}
		}
	}

	private boolean testAFDead(ISparsePetriNet spn) {
		try {
			Set<Integer> safe = StructuralReduction.findSCCSuffixes(spn, ReductionType.DEADLOCKS, new BitSet());			
		} catch (DeadlockFound e) {
			return true;
		}
		return false;
	}

}
