package fr.lip6.move.gal.application.runner.its;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.TimeoutException;

import fr.lip6.move.gal.Specification;
import fr.lip6.move.gal.application.mcc.MccTranslator;
import fr.lip6.move.gal.instantiate.Instantiator;
import fr.lip6.move.gal.semantics.IDeterministicNextBuilder;
import fr.lip6.move.gal.semantics.INextBuilder;
import fr.lip6.move.gal.structural.StructuralReduction;
import fr.lip6.move.gal.structural.StructuralToGreatSPN;

public class MultiOrderRunner {

	public static String computeOrderWithGreatSPN(String pwd, String gspnpath, String orderHeur, MccTranslator reader,
			String orderff) {
		if (orderHeur != null && gspnpath != null) {
			// No hierarchy in this path
			try {
	
				INextBuilder nb = INextBuilder.build(reader.getSpec());
				IDeterministicNextBuilder idnb = IDeterministicNextBuilder.build(nb);
				StructuralReduction sr = new StructuralReduction(idnb);
				StructuralToGreatSPN s2gspn = new StructuralToGreatSPN();
				String gspnmodelff = pwd + "/gspn";
				s2gspn.transform(sr, gspnmodelff);
				try {
					GreatSPNRunner pinvrun = new GreatSPNRunner(pwd, gspnmodelff, gspnpath + "/bin/pinvar", 30);
					pinvrun.run();
				} catch (TimeoutException e) {
					System.out.println("P-invariant computation with GreatSPN timed out. Skipping.");
				}
	
				GreatSPNRunner run = new GreatSPNRunner(pwd, gspnmodelff, gspnpath + "/bin/RGMEDD2", 60);
				run.configure("-" + orderHeur);
				run.configure("-varord-only");
				run.run();
				String[] order = run.getOrder();
	
				Specification reduced = sr.rebuildSpecification();
				reduced.getProperties().addAll(reader.getSpec().getProperties());
				Instantiator.normalizeProperties(reduced);
				reader.setSpec(reduced);
	
				orderff = pwd + "/" + "model.ord";
				PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(orderff)));
				out.println("#TYPE " + reduced.getMain().getName());
				for (int i = 0; i < order.length; i++) {
					String var = order[i];
					out.println(var);
				}
				out.println("#END");
				out.flush();
				out.close();
	
				System.out.println("Using order generated by GreatSPN with heuristic : " + orderHeur);
			} catch (TimeoutException e) {
				System.out.println("Order computation with GreatSPN timed out. Skipping.");
			} catch (Exception e) {
	
				e.printStackTrace();
			}
		}
		return orderff;
	}

}
