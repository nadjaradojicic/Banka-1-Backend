package com.banka1.banking.services;

import com.banka1.banking.dto.CreateCardDTO;
import com.banka1.banking.dto.CustomerDTO;
import com.banka1.banking.dto.NotificationDTO;
import com.banka1.banking.dto.request.CreateAccountDTO;
import com.banka1.banking.dto.request.UpdateAccountDTO;
import com.banka1.banking.listener.MessageHelper;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.Transaction;
import com.banka1.banking.models.helper.*;
import com.banka1.banking.repository.AccountRepository;

import com.banka1.banking.repository.TransactionRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;


@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final JmsTemplate jmsTemplate;
    private final MessageHelper messageHelper;
    private final ModelMapper modelMapper;
    private final String destinationEmail;
    private final UserServiceCustomer userServiceCustomer;
    private final CardService cardService;

    public AccountService(AccountRepository accountRepository, JmsTemplate jmsTemplate, MessageHelper messageHelper, ModelMapper modelMapper, @Value("${destination.email}") String destinationEmail, UserServiceCustomer userServiceCustomer, CardService cardService) {
        this.accountRepository = accountRepository;
        this.jmsTemplate = jmsTemplate;
        this.messageHelper = messageHelper;
        this.modelMapper = modelMapper;
        this.destinationEmail = destinationEmail;
        this.userServiceCustomer = userServiceCustomer;
        this.cardService = cardService;
    }

    public Account createAccount(CreateAccountDTO createAccountDTO, Long employeeId) {
        CustomerDTO owner = userServiceCustomer.getCustomerById(createAccountDTO.getOwnerID());
        if (owner == null || employeeId == null) {
            return null;
        }

        if ((createAccountDTO.getType().equals(AccountType.CURRENT) && !createAccountDTO.getCurrency().equals(CurrencyType.RSD)) ||
                (createAccountDTO.getType().equals(AccountType.FOREIGN_CURRENCY) && createAccountDTO.getCurrency().equals(CurrencyType.RSD))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nevalidna kombinacija vrste racuna i valute");
        }

        Account account = modelMapper.map(createAccountDTO, Account.class);

        if (account.getBalance() != null) {
            account.setBalance(account.getBalance());
        } else {
            account.setBalance(0.0);
        }

        account.setReservedBalance(100.0);
        account.setCreatedDate(Instant.now().getEpochSecond());
        account.setExpirationDate(account.getCreatedDate() + 4 * 365 * 24 * 60 * 60);
        account.setDailySpent(0.0);
        account.setMonthlySpent(0.0);
        account.setMonthlyMaintenanceFee(0.0);

        account.setAccountNumber(generateAccountNumber(account));

        account.setEmployeeID(employeeId);

        account = accountRepository.save(account);

        if (createAccountDTO.getCreateCard()) {
            CreateCardDTO createCardDTO = new CreateCardDTO();
            createCardDTO.setAccountID(account.getId());
            createCardDTO.setCardBrand(CardBrand.VISA);
            createCardDTO.setCardType(CardType.CREDIT);
            createCardDTO.setAuthorizedPerson(null);
            cardService.createCard(createCardDTO);
        }

        NotificationDTO emailDTO = new NotificationDTO();
        emailDTO.setSubject("Račun uspešno kreiran");
        emailDTO.setEmail(owner.getEmail());
        emailDTO.setMessage("Vaš racun je uspešno kreiran");
        emailDTO.setFirstName(owner.getFirstName());
        emailDTO.setLastName(owner.getLastName());
        emailDTO.setType("email");

        jmsTemplate.convertAndSend(destinationEmail, messageHelper.createTextMessage(emailDTO));

        return account;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public List<Account> getAccountsByOwnerId(Long ownerId) {
        return accountRepository.findByOwnerID(ownerId);
    }

    public Account findById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Račun sa ID-jem " + accountId + " nije pronađen"));
    }

    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Račun sa brojem " + accountNumber + " nije pronađen"));
    }

    public Account updateAccount(Long accountId, UpdateAccountDTO updateAccountDTO) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Račun sa ID-jem " + accountId + " nije pronađen"));

        Optional.ofNullable(updateAccountDTO.getDailyLimit()).ifPresent(account::setDailyLimit);
        Optional.ofNullable(updateAccountDTO.getMonthlyLimit()).ifPresent(account::setMonthlyLimit);
        Optional.ofNullable(updateAccountDTO.getStatus()).ifPresent(account::setStatus);

        return accountRepository.save(account);
    }

    @Autowired
    private TransactionRepository transactionRepository;
    public List<Transaction> getTransactionsForAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Račun sa ID-jem " + accountId + " nije pronađen"));

        List<Transaction> transactionsFrom = transactionRepository.findByFromAccountId(account);
        List<Transaction> transactionsTo = transactionRepository.findByToAccountId(account);

        List<Transaction> allTransactions = new ArrayList<>();
        allTransactions.addAll(transactionsFrom);
        allTransactions.addAll(transactionsTo);

        return allTransactions;
    }

    public static String generateAccountNumber(Account account){
        StringBuilder sb = new StringBuilder();
        sb.append("111");
        sb.append("0001");

        Random random = new Random();
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10)); // Add a random digit between 0-9
        }

        if (account.getType().equals(AccountType.CURRENT)) sb.append("1");
        else sb.append("2");

        switch (account.getSubtype()) {
            case PERSONAL -> sb.append("1");
            case BUSINESS -> sb.append("2");
            case SAVINGS -> sb.append("3");
            case PENSION -> sb.append("4");
            case YOUTH -> sb.append("5");
            case STUDENT -> sb.append("6");
            case STANDARD -> sb.append("7");
        }

        return sb.toString();
    }
}
