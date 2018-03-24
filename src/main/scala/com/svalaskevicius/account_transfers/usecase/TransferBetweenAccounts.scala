package com.svalaskevicius.account_transfers.usecase

import cats.Monad
import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.PositiveNumber
import com.svalaskevicius.account_transfers.service.AccountService

class TransferBetweenAccounts[F[_]: Monad] (accountService: AccountService[F]) {
  def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber) = ???
}
