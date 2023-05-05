package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import com.crio.warmup.stock.PortfolioManagerApplication;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {


 
  
    private RestTemplate restTemplate;
    private StockQuotesService stockQuotesService;
  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  protected PortfolioManagerImpl(StockQuotesService stockQuotesService) {
      
        this.stockQuotesService = stockQuotesService;
  }
  
  // @Deprecated
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
       this.restTemplate = restTemplate;
}



  //TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from main anymore.
  //    Copy your code from Module#3 PortfolioManagerApplication#calculateAnnualizedReturn
  //    into #calculateAnnualizedReturn function here and ensure it follows the method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required further as our
  //    clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command below:
  // ./gradlew test --tests PortfolioManagerTest

  //CHECKSTYLE:OFF

  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  //CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  //  Extract the logic to call Tiingo third-party APIs to a separate function.
  //  Remember to fill out the buildUri function and use that.


  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
  throws StockQuoteServiceException, JsonProcessingException {

  return stockQuotesService.getStockQuote(symbol, from, to);
}

protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
  //  String uriTemplate = "https:api.tiingo.com/tiingo/daily/$SYMBOL/prices?"
  //       + "startDate=$STARTDATE&endDate=$ENDDATE&token=$APIKEY";

  //       return uriTemplate;

        String url = "https://api.tiingo.com/tiingo/daily/"+ symbol +"/prices?startDate="+startDate+"&endDate="+endDate+"&token=15b187e2ee850381bf2f45d5249212feff423dff"; 

        return url;
}

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate) throws StockQuoteServiceException {
      
        // new PortfolioManagerImpl(new RestTemplate());   

        return portfolioTrades.stream().map(trade -> {
            List<Candle> candles = null;
            
              try {
                candles = getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
              } catch (StockQuoteServiceException e) {
                System.out.print(e.getMessage());
              } catch (JsonProcessingException e) {
                e.printStackTrace();
              } 
            
          double openPrice = candles.get(0).getOpen();
          double closePrice = candles.get(candles.size()-1).getClose();

          AnnualizedReturn annualizedReturn = PortfolioManagerApplication.calculateAnnualizedReturns(endDate, trade, openPrice, closePrice);
          return annualizedReturn;

       }).sorted(getComparator())
       .collect(Collectors.toList());
  }

  public AnnualizedReturn getAnnulaizedeturn(LocalDate endDate , PortfolioTrade trade) throws StockQuoteServiceException{
      
    AnnualizedReturn annualizedReturn  = null;
    List<Candle> candles = null;
    try {
      candles = getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
      double openPrice = candles.get(0).getOpen();
      double closePrice = candles.get(candles.size()-1).getClose();
  
      double totalVal = (closePrice - openPrice) / openPrice;
      double total_num_years = ChronoUnit.DAYS.between(trade.getPurchaseDate(), endDate) / 365.24;

      double annualized_returns = Math.pow((1 + totalVal), (1 / total_num_years)) - 1;
      
      annualizedReturn =  new AnnualizedReturn(trade.getSymbol(), annualized_returns, totalVal);

    } catch (JsonProcessingException e) {
       e.printStackTrace();
    }
   return annualizedReturn;
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
      List<PortfolioTrade> portfolioTrades, LocalDate endDate, int numThreads)
      throws InterruptedException, StockQuoteServiceException {

      long  startTime = System.currentTimeMillis();
       
    List<AnnualizedReturn> annualizedReturns = new ArrayList<AnnualizedReturn>();
    List<Future<AnnualizedReturn>> futureReturnsList = new ArrayList<Future<AnnualizedReturn>>();
    final ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    for (int i = 0; i < portfolioTrades.size(); i++) {
      PortfolioTrade trade = portfolioTrades.get(i);
      Callable<AnnualizedReturn> callableTask = () -> {
         return  getAnnulaizedeturn(endDate, trade);
      };
      Future<AnnualizedReturn> futureReturns = pool.submit(callableTask);
      futureReturnsList.add(futureReturns);
    }
  
    for (int i = 0; i < portfolioTrades.size(); i++) {
      Future<AnnualizedReturn> futureReturns = futureReturnsList.get(i);
      try {
        AnnualizedReturn returns = futureReturns.get();
        annualizedReturns.add(returns);
      } catch (ExecutionException e) {
        throw new StockQuoteServiceException("Error when calling the API", e);
  
      }
    }
    Collections.sort(annualizedReturns,getComparator());
    // For debugging purpose
    System.err.println(System.currentTimeMillis() - startTime );
    return annualizedReturns;
  }
  
}
