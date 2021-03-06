/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.teiid.translator.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;

import org.teiid.language.Argument;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ProcedureExecution;
import org.teiid.translator.TranslatorException;

public class LDAPDirectCreateUpdateDeleteQueryExecution implements ProcedureExecution {
	private static final String ATTRIBUTES = "attributes"; //$NON-NLS-1$
	private List<Argument> arguments;
	protected LdapContext ldapConnection;
	protected LDAPExecutionFactory executionFactory;
	protected ExecutionContext executionContext;
	private int updateCount = -1;
	private boolean returnsArray = true;
	private String query;
	
	public LDAPDirectCreateUpdateDeleteQueryExecution(List<Argument> arguments, LDAPExecutionFactory factory, ExecutionContext executionContext, LdapContext connection, String query, boolean returnsArray) {
		this.arguments = arguments;
		this.executionFactory = factory;
		this.executionContext = executionContext;
		this.ldapConnection = connection;
		this.query = query;
		this.returnsArray = returnsArray;
	}
	
	@Override
	public void execute() throws TranslatorException {
		String firstToken = null;
		
		StringTokenizer st = new StringTokenizer(query, ";"); //$NON-NLS-1$
		if (st.hasMoreTokens()) {
			firstToken = st.nextToken();
		}
		if (firstToken == null || (!firstToken.equalsIgnoreCase("create") && !firstToken.equalsIgnoreCase("update") && !firstToken.equalsIgnoreCase("delete"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12009));
		}		
		LdapContext ldapCtx = null;
		try {
			ldapCtx = (LdapContext)this.ldapConnection.lookup("");  //$NON-NLS-1$
		} catch (NamingException ne) {
			throw new TranslatorException(LDAPPlugin.Util.getString("LDAPUpdateExecution.createContextError",ne.getExplanation()));//$NON-NLS-1$
		}		

		if (firstToken.equalsIgnoreCase("delete")) { // //$NON-NLS-1$
			String theDN = getDN(st); // the token after the marker is always DN
			if (st.hasMoreTokens()) {
				throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12013, st.nextToken()));
			}
			try {
				ldapCtx.destroySubcontext(theDN);
				this.updateCount = 1;
			} catch (NamingException ne) {
				throw new TranslatorException(LDAPPlugin.Util.getString("LDAPUpdateExecution.deleteFailed",theDN,ne.getExplanation()));//$NON-NLS-1$
			} catch (Exception e) {
				throw new TranslatorException(e, LDAPPlugin.Util.getString("LDAPUpdateExecution.deleteFailedUnexpected",theDN));//$NON-NLS-1$
			}			
		}
		else if (firstToken.equalsIgnoreCase("create")) { //$NON-NLS-1$
			String theDN = getDN(st); // the token after the marker is always DN
			ArrayList<BasicAttribute> attributes = getAttributes(st);
			if (st.hasMoreTokens()) {
				throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12013, st.nextToken()));
			}
			BasicAttributes attrs = new BasicAttributes();
			for (BasicAttribute ba:attributes) {
				attrs.put(ba);
			}
			try {
				ldapCtx.createSubcontext(theDN, attrs);
				this.updateCount = 1;
			} catch (NamingException ne) {
				throw new TranslatorException(LDAPPlugin.Util.getString("LDAPUpdateExecution.insertFailed", theDN, ne.getExplanation()));//$NON-NLS-1$
			} catch (Exception e) {
				throw new TranslatorException(e,LDAPPlugin.Util.getString("LDAPUpdateExecution.insertFailedUnexpected", theDN));//$NON-NLS-1$
			}			
		}
		else if (firstToken.equalsIgnoreCase("update")) { //$NON-NLS-1$
			String theDN = getDN(st); // the token after the marker is always DN
			ArrayList<BasicAttribute> attributes = getAttributes(st);
			if (st.hasMoreTokens()) {
				throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12013, st.nextToken()));
			}
			ModificationItem[] updateMods = new ModificationItem[attributes.size()];
			int i=0;
			for (BasicAttribute ba:attributes) {
				updateMods[i++] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, ba);
			}		
			try {
				ldapCtx.modifyAttributes(theDN, updateMods);
				this.updateCount = 1;
			} catch (NamingException ne) {
				throw new TranslatorException(LDAPPlugin.Util.getString("LDAPUpdateExecution.updateFailed", theDN, ne.getExplanation()));//$NON-NLS-1$
			} catch (Exception e) {
				throw new TranslatorException(e, LDAPPlugin.Util.getString("LDAPUpdateExecution.updateFailedUnexpected",theDN));//$NON-NLS-1$
			}			
		}
	}
	
	private String getDN(StringTokenizer st) throws TranslatorException {
		if (!st.hasMoreTokens()) {
			throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12010));
		}
		return st.nextToken();
	}

	private ArrayList<BasicAttribute> getAttributes(StringTokenizer st) throws TranslatorException {
		if (!st.hasMoreTokens()) {
			throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12011));
		}
		
		ArrayList<BasicAttribute> attributes = new ArrayList<BasicAttribute>();
		
		if(st.hasMoreElements()) {
			String var = st.nextToken();
			
			int index = var.indexOf('=');
			if (index == -1) {
				throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12011));
			}
			String key = var.substring(0, index).trim();
			String value = var.substring(index+1).trim();
			
			if (key.equalsIgnoreCase(ATTRIBUTES)) {
				StringTokenizer attrTokens = new StringTokenizer(value, ","); //$NON-NLS-1$
				int attrCount = 0;
				while(attrTokens.hasMoreElements()) {
					String name = attrTokens.nextToken().trim();
					if (arguments.size() <= attrCount) {
						throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12012, name));
					}
					Argument argument = arguments.get(attrCount++);
					Object  anObj = null;
					if (argument.getArgumentValue().getValue() != null) {
						anObj = IQueryToLdapSearchParser.getExpressionString(argument.getArgumentValue());
					}
					
					attributes.add(new BasicAttribute(name, anObj));
				}
			} else {
				throw new TranslatorException(LDAPPlugin.Util.gs(LDAPPlugin.Event.TEIID12011));
			}
		}
		return attributes;
	}

	@Override
	public List<?> getOutputParameterValues() throws TranslatorException {
		return null;
	}

	@Override
	public List<?> next() throws TranslatorException, DataNotAvailableException {
		if (this.updateCount != -1) {
			List<Object> row = Arrays.asList((Object)Integer.valueOf(this.updateCount));
			if (returnsArray) {
				Object[] columns = new Object[1];
				columns[0] = this.updateCount;
				row.set(0, columns);
			}
			this.updateCount = -1;
			return row;
		}
		return null;
	}

	@Override
	public void close() {
	}

	@Override
	public void cancel() throws TranslatorException {
	}
}
