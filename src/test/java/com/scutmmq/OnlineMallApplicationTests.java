package com.scutmmq;

import com.scutmmq.entity.Product;
import com.scutmmq.enums.LoginType;
import com.scutmmq.service.ProductService;
import com.scutmmq.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.scutmmq.utils.RedisConstants.ORDER_TIMEOUT_TRIGGER;
import static com.scutmmq.utils.RedisConstants.PRODUCT_STOCK_AVAILABLE;

@SpringBootTest
@Slf4j
class OnlineMallApplicationTests {

    @Autowired
    private  ProductService productService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {
    }

    void  addRedisStock(){
        final List<Product> list = productService.list();
        for(Product product:list){
            redisTemplate.opsForValue().set(PRODUCT_STOCK_AVAILABLE+product.getId(),String.valueOf(product.getStockQuantity()));
        }
    }
    void getRedisMap(){
        String tempOrderId =(String) redisTemplate.opsForHash().get(
                RedisConstants.ORDER_ID_MAP_TO_TEMP_ID, "37");
        System.out.println(tempOrderId);
    }

    void addPendingOrder() throws InterruptedException {
        for(int i = 1;i<=10;i++){
            Thread.sleep(1000);
            redisTemplate.opsForZSet().add(ORDER_TIMEOUT_TRIGGER,String.valueOf(i),System.currentTimeMillis()+1000*60*10);//10分钟后过期
        }
    }

    void checkOutTimeOutOrder(){
        while (true){
            try {
                Thread.sleep(1000);
                System.out.println("当前时间戳:"+System.currentTimeMillis());
                final Set<ZSetOperations.TypedTuple<String>> set = redisTemplate.opsForZSet().rangeByScoreWithScores(ORDER_TIMEOUT_TRIGGER, 0, System.currentTimeMillis());
                if(set==null||set.isEmpty()){
                    System.out.println("未检测到超时订单");
                    continue;
                }
                System.out.println("检测到超时订单:");
                for (ZSetOperations.TypedTuple<String> tuple : set) {
                    System.out.println("value:"+tuple.getValue()+" score:"+tuple.getScore());
                }
                // TODO 发送给消息队列 删除过期订单 设置为已取消

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void handleTimeoutOrderMQ(){
        final List<MapRecord<String, Object, Object>> mapRecordList = redisTemplate.opsForStream().read(
                Consumer.from(RedisConstants.ORDER_TIMEOUT_GROUP, "c1"),
                StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
                StreamOffset.create(RedisConstants.ORDER_TIMEOUT_STREAM, ReadOffset.from("0"))
        );
        for (MapRecord<String, Object, Object> entries : mapRecordList) {
            System.out.println(entries.getValue());
        }
    }

    void signInNowDate(){
        Long userId = 22L;
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;

        // 今天是第几天
        int dayOfMouth = now.getDayOfMonth()-1;


        for(int i = 11 ;i<=23;i++){
            // 签到
            final Boolean aBoolean = redisTemplate.opsForValue().setBit("sign:user:22:2025-10"
                    , i, true);
            if(Boolean.TRUE.equals(aBoolean)){
                System.out.println("签到成功!");
            }
        }


    }

    void countSignIn(){
        Long userId = 11L;
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;
        int dayOfMouth = now.getDayOfMonth();
        final List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(5)).valueAt(0));

        if(result==null||result.isEmpty()){
            System.out.println("未签到");
        }

        Long num = result.get(0);

        int count = 0;

        while ((num & 1) != 0) {
            count++;
            num = num >>> 1;
        }

        System.out.println("连续签到"+count+"天");
    }

    void totalSignIn(){
        Long userId = 22L;
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;
        int dayOfMouth = now.getDayOfMonth();
        final List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(5)).valueAt(0));
        if(result==null||result.isEmpty()){
            System.out.println("未签到");
        }
        Long num = result.get(0);
        int count = 0;
        for(int i = dayOfMouth;i>=0;i--){
            if((num&1)==1)count++;
            num = num >>> 1;
        }

        System.out.println("累计签到"+count+"天");
    }

    void  getSignDays(){
        Long userId = 22L;
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;

        int dayOfMouth = now.getDayOfMonth();
        List<Integer> signedDays = new ArrayList<>();
        for (int offset = 0; offset < dayOfMouth; offset++){
            final Boolean aBoolean = redisTemplate.opsForValue().getBit(key, offset);
            if(Boolean.TRUE.equals(aBoolean)){
                signedDays.add(offset+1);
            }
        }
        System.out.println("签到的日期:"+signedDays);
    }
}
