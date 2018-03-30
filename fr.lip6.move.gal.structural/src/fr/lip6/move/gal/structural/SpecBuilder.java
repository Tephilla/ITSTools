package fr.lip6.move.gal.structural;

import java.util.List;

import android.util.SparseIntArray;
import fr.lip6.move.gal.BooleanExpression;
import fr.lip6.move.gal.ComparisonOperators;
import fr.lip6.move.gal.GALTypeDeclaration;
import fr.lip6.move.gal.GF2;
import fr.lip6.move.gal.GalFactory;
import fr.lip6.move.gal.Specification;
import fr.lip6.move.gal.Statement;
import fr.lip6.move.gal.Transition;
import fr.lip6.move.gal.Variable;
import fr.lip6.move.gal.util.MatrixCol;

public class SpecBuilder {

	
	public static Specification buildSpec (MatrixCol flowPT, MatrixCol flowTP, List<String> pnames, List<String> tnames, List<Integer> initial) {
		GalFactory gf = GalFactory.eINSTANCE;
		Specification spec = gf.createSpecification();
		
		GALTypeDeclaration gal = gf.createGALTypeDeclaration();
		gal.setName("petri");
		
		int vi = 0;
		for (String var : pnames) {
			Variable v = gf.createVariable();
			// deal with hierarchy...
			String[] split = var.split(":");			
			if (split.length > 1) {
				var = split[split.length-1];
			}
			v.setName(var);
			v.setValue(GF2.constant(initial.get(vi)));
			gal.getVariables().add(v);
			vi++;
		}
		int ti = 0;
		for (String tname : tnames) {
			Transition trans = gf.createTransition();
			trans.setName(tname);
			BooleanExpression guard = gf.createTrue();
			SparseIntArray inputs = flowPT.getColumn(ti);
			for (int i = 0; i < inputs.size(); i++) {
				Variable place = gal.getVariables().get(inputs.keyAt(i));
				int val = inputs.valueAt(i);
				guard = GF2.and(guard, GF2.createComparison(GF2.createVariableRef(place), ComparisonOperators.GE, GF2.constant(val)));
			}
			trans.setGuard(guard);
			SparseIntArray outputs = flowTP.getColumn(ti);
			SparseIntArray flow = SparseIntArray.deltaSum(inputs, outputs);
			for (int i=0 ; i < flow.size() ; i++) {
				Variable v = gal.getVariables().get(flow.keyAt(i));
				Statement ass = GF2.createIncrement(v, flow.valueAt(i));
				trans.getActions().add(ass);
			}
			gal.getTransitions().add(trans);
			ti++;
		}
		
		spec.getTypes().add(gal);
		spec.setMain(gal);
		return spec;
	}

}
