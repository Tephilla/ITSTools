/*
 * generated by Xtext
 */
package fr.lip6.move.gal;

import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.impl.ImportUriGlobalScopeProvider;

import fr.lip6.move.gal.scoping.GalLogicScopeProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
public class LogicRuntimeModule extends fr.lip6.move.gal.AbstractLogicRuntimeModule {

	@Override
	public Class<? extends IScopeProvider> bindIScopeProvider() {
		return GalLogicScopeProvider.class;
	}
	
	@Override
	public Class<? extends IGlobalScopeProvider> bindIGlobalScopeProvider() {
		return ImportUriGlobalScopeProvider.class;
	}
	
}
