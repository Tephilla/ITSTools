package fr.lip6.move.gal.structural;

import java.util.List;
import java.util.logging.Logger;

import android.util.SparseIntArray;
import fr.lip6.move.gal.structural.expr.Expression;
import fr.lip6.move.gal.structural.expr.Op;

public class LogicSimplifier {

	
	public static int simplifyWithDead(List<Property> properties, SparsePetriNet sparsePetriNet) {
		int simplified = 0;
		for (Property prop : properties) {
			switch (prop.getType()) {
			case INVARIANT: {
				Expression body = prop.getBody();
				if (body.getOp() == Op.BOOLCONST) {
					continue;
				}
				// simplify(body);
				int eval = evalInDeadlock(body.childAt(0));
				if (eval == -1 && body.getOp()==Op.AG) {
					prop.setBody(Expression.constant(false));
					simplified++;
				} else if (eval == 1 && body.getOp()==Op.EF) {
					prop.setBody(Expression.constant(true));
					simplified++;
				}
			}
			case LTL:
			case CTL:
			default:
				continue;
			}
		}
		if (simplified != 0) {
			System.out.println("AF dead knowledge conclusive for " + simplified + " formulas.");
		}
		return simplified;
	}

	
	
	private static int evalInDeadlock(Expression body) {
		if (body.getOp() == Op.ENABLED) {
			return -1;
		} else if (body.getOp() == Op.OR) {			
			for (int i=0,ie=body.nbChildren() ; i < ie ; i++) {
				Expression c = body.childAt(i);
				int v = evalInDeadlock(c);
				if (v==0) {
					return 0;					
				} else if (v==1) {
					return 1;
				}
			}
			return -1;			
		} else if (body.getOp() == Op.AND) {
			for (int i=0,ie=body.nbChildren() ; i < ie ; i++) {
				Expression c = body.childAt(i);
				int v = evalInDeadlock(c);
				if (v==0) {
					return 0;					
				} else if (v==-1) {
					return -1;
				}
			}
			return 1;						
		} else if (body.getOp() == Op.NOT) {
			return -evalInDeadlock(body.childAt(0));
		} else {
			return 0;			
		}
	}



	public static int simplifyWithInitial(List<Property> properties, SparseIntArray spinit, SparsePetriNet spn) {

		int simplified = 0;
		for (Property prop : properties) {
			switch (prop.getType()) {
			case LTL:
			case CTL:
			case INVARIANT: {
				Expression body = prop.getBody();
				if (body.getOp() == Op.BOOLCONST) {
					continue;
				}
				// simplify(body);
				int eval = evalInInitial(body, spinit, spn);
				if (eval == 1) {
					prop.setBody(Expression.constant(true));
				} else if (eval == -1) {
					prop.setBody(Expression.constant(false));
				}
				if (eval != 0) {
					simplified++;
				}
			}
			default:
				continue;
			}
		}
		if (simplified != 0) {
			System.out.println("Initial state reduction rules removed " + simplified + " formulas.");
		}
		return simplified;
	}

	/**
	 * This function is an adaptation to LTL/CTL of the Initial states simplification
	 * strategy proposed in Section 3 of the paper : Simplification of CTL Formulae
	 * for Efficient Model Checking of Petri Nets Published at IC PetriNets'2018 By
	 * Bonneland, et al. of Jiri Srba's group working on Tapaal.
	 * @param spn 
	 */
	private static int evalInInitial(Expression predicate, SparseIntArray init, SparsePetriNet spn) {
		switch (predicate.getOp()) {

		case BOOLCONST: {
			if (predicate.getValue() == 1) {
				return 1;
			} else {
				return -1;
			}
		}
		case LT:case LEQ:case EQ:case NEQ:case GEQ:case GT: {
			if (predicate.eval(init) == 1) {
				return 1;
			} else {
				return -1;
			}
		}
		case ENABLED: {
			for (int i=0; i < predicate.nbChildren(); i++) {
				Expression e = predicate.childAt(i);
				if (e.getOp()!=Op.TRANSREF) {
					System.out.println("Unexpected child of enabling was not a transitions reference.");
					return 0;
				} else {
					int tid = e.getValue();
					if (SparseIntArray.greaterOrEqual(init, spn.getFlowPT().getColumn(tid))) {
						if (spn.isSkeleton()) {
							return 0;
						} else {
							return 1;
						}
					}
				}
			}
			return -1;
		}			
		case NOT:
			return -evalInInitial(predicate.childAt(0), init, spn);
		case AND: {
			boolean found0 = false;

			for (int i = 0; i < predicate.nbChildren(); i++) {
				Expression c = predicate.childAt(i);
				int value = evalInInitial(c, init, spn);

				if (value == -1) {
					// we are also -1
					return -1;
				} else if (value == 0) {
					found0 = true;
				}
			}

			if (found0) {
				return 0;
			} else {
				return 1;
			}
		}
		case OR: {
			boolean found0 = false;

			for (int i = 0; i < predicate.nbChildren(); i++) {
				Expression c = predicate.childAt(i);
				int value = evalInInitial(c, init, spn);

				if (value == 1) {
					// we are also 1
					return 1;
				} else if (value == 0) {
					found0 = true;
				}
			}

			if (found0) {
				return 0;
			} else {
				return -1;
			}
		}
		case X:case EX:case AX:
			return 0;
		case G:case EG:case AG:{
			if (evalInInitial(predicate.childAt(0), init, spn) == -1) {
				return -1;
			} else {
				return 0;
			}
		}
		case F:case EF:case AF:{
			if (evalInInitial(predicate.childAt(0), init, spn) == 1) {
				return 1;
			} else {
				return 0;
			}
		}
		case U:case EU:case AU: {
			int evalr = evalInInitial(predicate.childAt(1), init, spn);
			if (evalr == 1) {
				return 1;
			} else if (evalr == -1) {
				if (evalInInitial(predicate.childAt(0), init, spn) == -1) {
					return -1;
				}
			}
			return 0;			
		}
		default:
			Logger.getLogger("fr.lip6.move.gal").warning("When simplifiying with initial state, unexpected operator in formula :" + predicate.getOp());
			return 0;
		}		
	}


}
