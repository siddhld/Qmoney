package com.crio.warmup.stock;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import com.crio.warmup.stock.log.UncaughtExceptionHandler;
import com.crio.warmup.stock.portfolio.PortfolioManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.dto.TotalReturnsDto;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.stream.Stream;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.web.client.RestTemplate;


public class PortfolioManagerApplication {


     private static RestTemplate restTemplate = new RestTemplate() ;

   //   @Autowired
     private static  PortfolioManager portfolioManager ;

    public static String getToken() {
      return "c9bfd5f03f9a0aabaddb81872dd2c0514a7e87ad";
   }

   public static PortfolioTrade[] listToArray(List<PortfolioTrade> trades){
      return trades.stream().toArray(PortfolioTrade[]::new);
   }

  
   public static List<String> mainReadQuotes(String[] args) throws IOException, URISyntaxException {

      PortfolioTrade[] trades = listToArray(readTradesFromJson(args[0]));
         LocalDate endDate = LocalDate.parse(args[1]);

         return Arrays.stream(trades).map(trade -> {         
            String url =  prepareUrl(trade ,endDate, getToken());
            TiingoCandle[] candles = restTemplate.getForObject(url, TiingoCandle[].class);           
            return new TotalReturnsDto(trade.getSymbol(), candles[candles.length-1].getClose());        
         }).sorted(Comparator.comparing(TotalReturnsDto::getClosingPrice))
         .map(TotalReturnsDto::getSymbol)
         .collect(Collectors.toList());
   }
   
  public static List<String> mainReadFile(String[] args) throws StreamReadException, DatabindException, IOException, URISyntaxException {
      List<String>  symbols  = new ArrayList<>();                                                                      
      PortfolioTrade[] trades = listToArray(readTradesFromJson(args[0]));
          for(PortfolioTrade trade: trades){          
           symbols.add(trade.getSymbol());
          }
      return symbols;    
   }
  

  public static File fileResolver(String string) throws StreamReadException, DatabindException, IOException{
     File file = new File("src/main/resources/"+string);
     return file;
  }
  
  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }


  public static List<PortfolioTrade> readTradesFromJson(String filename) throws IOException, URISyntaxException {
    
    File assFile =  fileResolver(filename);    
    ObjectMapper mapper = getObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    PortfolioTrade[] trades = mapper.readValue(assFile,PortfolioTrade[].class);         
    
      return Arrays.asList(trades);     
  }

  // TODO:
   // Build the Url using given parameters and use this function in your code to cann the API.
  public static String prepareUrl(PortfolioTrade trade, LocalDate endDate, String token) {    
    String url = "https://api.tiingo.com/tiingo/daily/"+ trade.getSymbol() +"/prices?startDate="+trade.getPurchaseDate()+"&endDate="+endDate+"&token="+token; 
    return url;
  }

  private static void printJsonObject(Object object) throws IOException {
    Logger logger = Logger.getLogger(PortfolioManagerApplication.class.getCanonicalName());
    ObjectMapper mapper = new ObjectMapper();
    logger.info(mapper.writeValueAsString(object));
  }


  public static List<String> debugOutputs() {

    String valueOfArgument0 = "trades.json";
    String resultOfResolveFilePathArgs0 = "trades.json";
    String toStringOfObjectMapper = "ObjectMapper";
    String functionNameFromTestFileInStackTrace = "mainReadFile";
    String lineNumberFromTestFileInStackTrace = "";


   return Arrays.asList(new String[]{valueOfArgument0, resultOfResolveFilePathArgs0,
       toStringOfObjectMapper, functionNameFromTestFileInStackTrace,
       lineNumberFromTestFileInStackTrace});
 }


  static Double getOpeningPriceOnStartDate(List<Candle> list) {
     return list.get(0).getOpen();
  }


  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
     return candles.get(candles.size() -1).getClose();
  }



  public static List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate, String token) {
     String url =  prepareUrl(trade ,endDate, token);
     TiingoCandle[] candles = restTemplate.getForObject(url, TiingoCandle[].class);

     List<Candle> candlesList = Arrays.asList(candles);
     return candlesList;
  }


  public static List<AnnualizedReturn> mainCalculateSingleReturn(String[] args)
      throws IOException, URISyntaxException {
         PortfolioTrade[] trades = listToArray(readTradesFromJson(args[0]));
         LocalDate endDate = LocalDate.parse(args[1]);

         return Arrays.stream(trades).map(trade -> {
            String url =  prepareUrl(trade ,endDate, getToken());            
            TiingoCandle[] candles =  restTemplate.getForObject(url, TiingoCandle[].class);                  
            
            double openPrice = candles[0].getOpen();
            double closePrice = candles[candles.length -1].getClose();

            AnnualizedReturn annualizedReturn = calculateAnnualizedReturns(endDate, trade, openPrice, closePrice);
            return annualizedReturn;

         }).sorted(Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed())
         .collect(Collectors.toList());
  }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate,
      PortfolioTrade trade, Double buyPrice, Double sellPrice) {
         double totalVal = (sellPrice - buyPrice) / buyPrice;
         double total_num_years = ChronoUnit.DAYS.between(trade.getPurchaseDate(), endDate) / 365.24;

         double annualized_returns = Math.pow((1 + totalVal), (1 / total_num_years)) - 1;
         
      return new AnnualizedReturn(trade.getSymbol(), annualized_returns, totalVal);
  }


  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
    ThreadContext.put("runId", UUID.randomUUID().toString());
  }

  public static List<AnnualizedReturn> mainCalculateReturnsAfterRefactor(String[] args)
      throws Exception {
       String file = args[0];
       LocalDate endDate = LocalDate.parse(args[1]);
   
      //  Object portfolioTrades;
      List<PortfolioTrade> portfolioTrades = readTradesFromJson(file);
      return portfolioManager.calculateAnnualizedReturn(portfolioTrades, endDate);
  }

  }
