-- 逐行选择，按Ctrl+Alt+Enter执行所选行

SET @LINK = 'https://www.mia.com/formulas.html';
SET @LINK2 = 'https://www.mia.com/item-2546428.html';

-- 1. 访问链接

SELECT DOM_LOAD(@LINK);

-- 2. 使用 CSS 选择器

SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '.welcome');
SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPrice', 0, 5);
SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic img', 0, 5);
SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic a', 0, 5);
SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), 'a[href~=item]', 0, 5);

-- 3. 使用 CSS 选择器，并提取属性

SELECT DOM_TEXT(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.welcome');
SELECT DOM_TEXT(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPrice', 0, 5);
SELECT DOM_SRC(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic img', 0, 5);

SELECT DOM_TITLE(DOM), DOM_ABS_HREF(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic a', 0, 5);
SELECT DOM_TITLE(DOM), DOM_ABS_HREF(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), 'a[href~=item]', 0, 5);

-- 4. 扩展 CSS ： 框

SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '*:in-box(*,*,323,31)');
SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '*:in-box(*,*,229,36)', 0, 5);
SELECT DOM_FIRST_TEXT(DOM_LOAD(@LINK), '229x36');

-- 5. 扩展 CSS ： 表达式

SELECT * FROM DOM_SELECT(DOM_LOAD(@LINK), '*:expr(_width==248 && _height==228)', 0, 5);
SELECT DOM_TITLE(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '*:expr(_width==248 && _height==228) a', 0, 5);

-- 6. 使用 SQL 替代 CSS 选择器

SELECT DOM_TITLE(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic a', 0, 5);
SELECT DOM_TITLE(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfPic a', 0, 5) WHERE LOCATE('白金版', DOM_TITLE(DOM)) > 0;
SELECT * FROM DOM_LOAD_AND_GET_FEATURES(@LINK) WHERE _WIDTH=248 AND _HEIGHT=228 LIMIT 100;

-- 7. 显示网页参数

SELECT * FROM DOM_LOAD_AND_GET_FEATURES(@LINK, '.nfList', 0, 20);
SELECT * FROM DOM_LOAD_AND_GET_FEATURES(@LINK2, 'DIV,UL,UI,P', 0, 20);

-- 8. 提取链接

SELECT DOM_SELECT(DOM_LOAD(@LINK), '.nfList a', 0, 5);
SELECT DOM_ABS_HREF(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfList a', 0, 5);

-- 9. 使用 CSS 表达式，并提取链接

SELECT DOM_ABS_HREF(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '*:expr(_width > 240 && _width < 250 && _height > 360 && _height < 370) a', 0, 5);

-- 10. 使用 SQL 表达式，并提取链接

SELECT * FROM DOM_LOAD_AND_GET_FEATURES(@LINK) WHERE _WIDTH BETWEEN 240 AND 250 AND _HEIGHT BETWEEN 360 AND 370 LIMIT 10;
SELECT DOM_ABS_HREF(DOM_SELECT_FIRST(DOM, 'a')) AS HREF FROM DOM_LOAD_AND_GET_FEATURES(@LINK) WHERE _WIDTH BETWEEN 240 AND 250 AND _HEIGHT BETWEEN 360 AND 370 LIMIT 10;
SELECT DOM_ABS_HREF(DOM_SELECT_FIRST(DOM, 'a')) AS HREF FROM DOM_LOAD_AND_GET_FEATURES(@LINK) WHERE _SIBLING > 250 LIMIT 10;

-- 11. 使用预定义函数提取链接

SELECT * FROM DOM_LOAD_AND_GET_LINKS(@LINK, '*:expr(_width > 240 && _width < 250 && _height > 360 && _height < 370)');

-- 12. 批量下载网页

-- SELECT DOM, DOM_TEXT(DOM) FROM DOM_SELECT(DOM_LOAD(@LINK), '.nfList', 0, 10);
-- SELECT DOM, DOM_TEXT(DOM) FROM DOM_LOAD_OUT_PAGES(@LINK, '*:expr(_width > 240 && _width < 250 && _height > 360 && _height < 370)', 0, 10);

SELECT * FROM VALUES
('使用说明', ''),
('1', '要运行此向导，请点击右上角“打开编辑器”，选择任意一行 SQL 语句（用鼠标或者键盘选择，所选文字应该为高亮状态），按Ctrl+Alt+Enter执行所选行'),
('2', '点击“更新”按钮显示本说明');
