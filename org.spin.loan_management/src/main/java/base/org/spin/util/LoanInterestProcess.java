/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2014 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpya.com                                  *
 *****************************************************************************/
package org.spin.util;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.spin.model.MFMAgreement;
import org.spin.model.MFMBatch;
import org.spin.model.MFMFunctionalSetting;
import org.spin.model.MFMTransaction;
import org.spin.model.MFMTransactionType;
import org.spin.util.AmortizationValue;

/**
 * Loan Daily Interest Calculation
 * Calculate Daily Interest
 * 			(A Variable)					(B Variable)
 * 			((1 + InterestRate) ^ (MonthlyDays / YEAR_DAY)) - 1
 *			Dunning Interest = (DaysDue * InterestRate)
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 *      <li> FR [ 1666 ] Calculate daily Loan Interest
 *		@see https://github.com/adempiere/adempiere/issues/1666
 */
public class LoanInterestProcess extends AbstractFunctionalSetting {

	public LoanInterestProcess(MFMFunctionalSetting setting) {
		super(setting);
	}

	@Override
	public String run() {
		MFMAgreement agreement = (MFMAgreement) getParameter(FinancialSetting.AGREEMENT_PO);
		MFMBatch batch = (MFMBatch) getParameter(FinancialSetting.BATCH_PO);
		//	Nothing
		if(agreement == null
				|| batch == null) {
			return null;
		}
		
		MFMTransactionType interestType = MFMTransactionType.getTransactionTypeFromType(getCtx(), MFMTransactionType.TYPE_LoanInterestCalculated);
		MFMTransactionType interestTaxType = MFMTransactionType.getTransactionTypeFromType(getCtx(), MFMTransactionType.TYPE_LoanTaxAmountCalculated);
		//	Validate
		if(interestType == null) {
			throw new AdempiereException("@FM_TransactionType_ID@ @NotFound@ " + MFMTransactionType.TYPE_LoanInterestCalculated);
		}
		//	
		HashMap<String, Object> returnValues = LoanUtil.calculateLoanInterest(getCtx(), agreement.getFM_Agreement_ID(), 
				new Timestamp(System.currentTimeMillis()), getFunctionalSetting().get_TrxName());
		//	Process it
		if(returnValues == null
				|| returnValues.isEmpty()) {
			return null;
		}
		//	Else
		List<AmortizationValue> amortizationList = (List<AmortizationValue>) returnValues.get("AMORTIZATION_LIST");
		if(amortizationList == null) {
			return null;
		}
		//	Iterate
		for (AmortizationValue row : amortizationList) {
			MFMTransaction transaction = batch.addTransaction(interestType.getFM_TransactionType_ID(), row.getInterestAmtFee());
			if(transaction != null) {
				transaction.set_ValueOfColumn("FM_Amortization_ID", row.getAmortizationId());
				transaction.saveEx();
			}
			if(interestTaxType != null
					&& row.getTaxAmtFee() != null) {
				transaction = batch.addTransaction(interestTaxType.getFM_TransactionType_ID(), row.getTaxAmtFee());
				if(transaction != null) {
					transaction.set_ValueOfColumn("FM_Amortization_ID", row.getAmortizationId());
					transaction.saveEx();
				}
			}
		}
		return null;
	}
}
