INSERT INTO payment_demo_user (user_id, nickname, account_tag) VALUES
    (1001, 'alice', 'new-user'),
    (1002, 'bob', 'active-user'),
    (1003, 'carol', 'vip-user');

INSERT INTO payment_demo_product (product_code, product_name, amount_fen, description) VALUES
    ('VIP_MONTH', '鎺ㄨ崘绯荤粺浼氬憳鏈堝崱', 9900, '鐢ㄤ簬娴嬭瘯鏅€氬崟娆¤喘涔版祦绋?),
    ('VIP_YEAR', '鎺ㄨ崘绯荤粺浼氬憳骞村崱', 59900, '鐢ㄤ簬娴嬭瘯楂橀噾棰濇敮浠樺崟'),
    ('REPORT_PACK', '鎺ㄨ崘鏁堟灉鍒嗘瀽鎶ュ憡鍖?, 19900, '鐢ㄤ簬娴嬭瘯鎺ㄨ崘绯荤粺澧炲€兼湇鍔¤喘涔?);

INSERT INTO payment_order (
    order_no,
    idempotency_key,
    user_id,
    product_code,
    amount_fen,
    status,
    channel_trade_no,
    paying_started_at,
    paid_at,
    closed_at,
    created_at,
    updated_at
) VALUES
    (
        'PAY_DEMO_SUCCESS_001',
        'IDEMP_DEMO_SUCCESS_001',
        1001,
        'VIP_MONTH',
        9900,
        'SUCCESS',
        'WX202603200001',
        '',
        '',
        NULL,
        '',
        ''
    ),
    (
        'PAY_DEMO_PAYING_001',
        'IDEMP_DEMO_PAYING_001',
        1002,
        'REPORT_PACK',
        19900,
        'PAYING',
        NULL,
        '',
        NULL,
        NULL,
        '',
        ''
    ),
    (
        'PAY_DEMO_CLOSED_001',
        'IDEMP_DEMO_CLOSED_001',
        1003,
        'VIP_YEAR',
        59900,
        'CLOSED',
        NULL,
        NULL,
        NULL,
        '',
        '',
        ''
    );

INSERT INTO seckill_stock (
    sku_id,
    activity_id,
    total_stock,
    available_stock,
    reserved_stock,
    sold_stock,
    version,
    updated_at
) VALUES
    (
        20001,
        30001,
        100,
        97,
        2,
        1,
        3,
        ''
    ),
    (
        20002,
        30001,
        20,
        20,
        0,
        0,
        0,
        ''
    );

INSERT INTO seckill_reservation (
    reservation_id,
    activity_id,
    sku_id,
    user_id,
    reservation_token,
    status,
    payment_order_no,
    expire_at,
    released_at,
    created_at,
    updated_at
) VALUES
    (
        'RSV_DEMO_ORDER_CREATED_001',
        30001,
        20001,
        1001,
        'TOKEN_DEMO_ORDER_CREATED_001',
        'ORDER_CREATED',
        'PAY_DEMO_PAYING_001',
        '',
        NULL,
        '',
        ''
    ),
    (
        'RSV_DEMO_PAID_001',
        30001,
        20001,
        1002,
        'TOKEN_DEMO_PAID_001',
        'PAID',
        'PAY_DEMO_SUCCESS_001',
        '',
        NULL,
        '',
        ''
    ),
    (
        'RSV_DEMO_RELEASED_001',
        30001,
        20001,
        1003,
        'TOKEN_DEMO_RELEASED_001',
        'RELEASED',
        NULL,
        '',
        '',
        '',
        ''
    );

INSERT INTO supplier_connection (
    supplier_id,
    supplier_code,
    status,
    pull_interval_seconds,
    next_pull_at,
    last_success_at,
    last_error_at,
    last_cursor,
    retry_count,
    lease_until,
    version,
    created_at,
    updated_at
) VALUES
    (
        9001,
        'SUPPLIER_ALPHA',
        'ACTIVE',
        60,
        '',
        '',
        NULL,
        'cursor-9001-v1',
        0,
        NULL,
        0,
        '',
        ''
    ),
    (
        9002,
        'SUPPLIER_FAIL_ONCE',
        'ACTIVE',
        120,
        '',
        NULL,
        NULL,
        NULL,
        0,
        NULL,
        0,
        '',
        ''
    ),
    (
        9003,
        'SUPPLIER_PAUSED',
        'PAUSED',
        300,
        '',
        NULL,
        NULL,
        NULL,
        0,
        NULL,
        0,
        '',
        ''
    );

-- 鈹€鈹€鈹€ 閲戣澏浜戞槦绌猴紙KD_ 鍓嶇紑锛変緵搴斿晢杩炴帴绉嶅瓙鏁版嵁 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- sandbox=true 鏃堕€傞厤鍣ㄨ繑鍥炲唴缃?mock 鏁版嵁锛屾棤闇€鐪熷疄閲戣澏鐜锛?-- 灏?application.yml 涓?supplier.erp.kingdee.sandbox 鏀逛负 false 骞跺～鍐欑湡瀹炲嚟璇佸嵆鍙帴鍏ョ湡瀹?ERP銆?INSERT INTO supplier_connection (
    supplier_id, supplier_code, status, pull_interval_seconds,
    next_pull_at, last_success_at, last_error_at, last_cursor,
    retry_count, lease_until, version, created_at, updated_at
) VALUES
    (
        -- 閲戣澏渚涘簲鍟?1锛氭甯歌繍琛岋紝宸插畬鎴愯繃涓€杞媺鍙?        9101,
        'KD_SUPPLIER_HANGZHOU',
        'ACTIVE',
        300,
        '',
        '',
        NULL,
        '2026-04-02 14:55:00|0',
        0,
        NULL,
        1,
        '',
        ''
    ),
    (
        -- 閲戣澏渚涘簲鍟?2锛氬緟棣栨鎷夊彇锛岀敤浜庢紨绀哄垎椤垫父鏍囦粠闆跺紑濮?        9102,
        'KD_SUPPLIER_SHENZHEN',
        'ACTIVE',
        600,
        '',
        NULL,
        NULL,
        NULL,
        0,
        NULL,
        0,
        '',
        ''
    ),
    (
        -- 閲戣澏渚涘簲鍟?3锛氭ā鎷熼壌鏉冨け璐ヨ鎸傝捣锛圓UTH_FAILURE 鈫?SUSPENDED锛?        9103,
        'KD_SUPPLIER_SUSPENDED',
        'SUSPENDED',
        300,
        '',
        NULL,
        '',
        NULL,
        3,
        NULL,
        5,
        '',
        ''
    );

-- 鈹€鈹€鈹€ 鐢ㄥ弸 BIP锛圷Y_ 鍓嶇紑锛変緵搴斿晢杩炴帴绉嶅瓙鏁版嵁 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- sandbox=true 鏃堕€傞厤鍣ㄨ繑鍥炲唴缃?mock 鏁版嵁锛屾棤闇€鐪熷疄鐢ㄥ弸 BIP 鐜銆?INSERT INTO supplier_connection (
    supplier_id, supplier_code, status, pull_interval_seconds,
    next_pull_at, last_success_at, last_error_at, last_cursor,
    retry_count, lease_until, version, created_at, updated_at
) VALUES
    (
        -- 鐢ㄥ弸渚涘簲鍟?1锛氭甯歌繍琛?        9201,
        'YY_SUPPLIER_BEIJING',
        'ACTIVE',
        300,
        '',
        '',
        NULL,
        '2026-04-02|1',
        0,
        NULL,
        1,
        '',
        ''
    ),
    (
        -- 鐢ㄥ弸渚涘簲鍟?2锛氭浘琚檺娴侊紝绛夊緟閲嶈瘯锛圧ATE_LIMITED 鍦烘櫙锛?        9202,
        'YY_SUPPLIER_SHANGHAI',
        'ACTIVE',
        600,
        '',
        '',
        '',
        '2026-04-02|1',
        2,
        NULL,
        3,
        '',
        ''
    );

