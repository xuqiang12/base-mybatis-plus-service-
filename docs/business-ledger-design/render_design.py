"""Render the reviewable business-ledger design posters. Requires Pillow only."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W = 2400
C = dict(bg='#F4F5F7', ink='#253039', sub='#66737B', line='#DEE3E7', paper='#FFFFFF',
         red='#EF6260', redbg='#FFF0ED', blue='#387EBE', bluebg='#EEF5FD',
         green='#27876B', greenbg='#EAF5EF', purple='#8061AD', purplebg='#F3EFF9', beige='#F5EDE0')
FONT = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'

class Poster:
    def __init__(self, h, number, title, subtitle):
        self.im = Image.new('RGB', (W,h), C['bg']); self.d = ImageDraw.Draw(self.im)
        self.text(65,42,'BUSINESS LEDGER   /   PRODUCT & FLOW DESIGN',24,C['green'])
        self.text(65,88,title,58,bold=True)
        self.text(65,175,subtitle,28,C['sub'])
        self.pill(2030,50,number+' / 03',C['greenbg'],C['green'],220)
        self.text(65,h-65,'2026.10.31  ·  项目需求设计稿  ·  所有金额为示例  ·  图中规划不代表已实现',25,C['sub'])
    def font(self,size,bold=False): return ImageFont.truetype(BOLD if bold else FONT,size)
    def text(self,x,y,s,size=28,color=None,bold=False):
        self.d.text((x,y),str(s),font=self.font(size,bold),fill=color or C['ink'])
    def wrap(self,s,maxw,size=28):
        arr=[]
        for para in str(s).split('\n'):
            line=''
            for char in para:
                if self.d.textlength(line+char,font=self.font(size))>maxw and line:
                    arr.append(line);line=char
                else: line+=char
            arr.append(line)
        return arr
    def para(self,x,y,s,maxw,size=28,color=None,gap=12):
        for line in self.wrap(s,maxw,size):
            self.text(x,y,line,size,color);y+=size+gap
        return y
    def rect(self,x,y,w,h,fill=None,r=24,outline=None):
        self.d.rounded_rectangle((x,y,x+w,y+h),radius=r,fill=fill or C['paper'],outline=outline or C['line'],width=2)
    def pill(self,x,y,s,bg,fg,w=None):
        w=w or int(self.d.textlength(s,font=self.font(24)))+36
        self.d.rounded_rectangle((x,y,x+w,y+46),radius=12,fill=bg)
        self.text(x+18,y+6,s,24,fg)
    def line(self,x,y,x2,y2,color=None): self.d.line((x,y,x2,y2),fill=color or C['line'],width=2)
    def section(self,y,n,title,tag=None):
        self.pill(65,y,n,C['greenbg'],C['green'],65)
        self.text(146,y-1,title,34,bold=True)
        if tag: self.text(146,y+48,tag,25,C['sub'])
    def arrow(self,x,y,x2,y2,color=None):
        col=color or C['green'];self.d.line((x,y,x2,y2),fill=col,width=4)
        import math
        a=math.atan2(y2-y,x2-x)
        pts=[(x2,y2),(x2-17*math.cos(a-.45),y2-17*math.sin(a-.45)),(x2-17*math.cos(a+.45),y2-17*math.sin(a+.45))]
        self.d.polygon(pts,fill=col)
    def card(self,x,y,w,h,title,lines,accent='green',size=28):
        self.rect(x,y,w,h)
        self.d.rounded_rectangle((x+22,y+25,x+29,y+67),radius=3,fill=C[accent])
        self.text(x+46,y+22,title,32,bold=True)
        cy=y+87
        for item in lines:
            cy=self.para(x+28,cy,item,w-56,size)+12
        if cy>y+h-12: raise ValueError(f'Overflow {title}: {cy} > {y+h}')
    def phone(self,x,y,w,h,title,active=0):
        self.rect(x,y,w,h,C['paper'],32)
        self.d.rounded_rectangle((x+2,y+2,x+w-2,y+92),radius=30,fill=C['beige'])
        self.d.rectangle((x+2,y+52,x+w-2,y+92),fill=C['beige'])
        self.text(x+26,y+25,title,32,bold=True)
        self.pill(x+w-160,y+22,'生意账本',C['paper'],C['sub'],140)
        self.line(x+2,y+h-83,x+w-2,y+h-83)
        for i,s in enumerate(['首页','客户','账单','我的']):
            col=C['red'] if i==active else C['sub']
            self.text(x+30+i*(w-40)/4,y+h-58,s,26,col,bold=i==active)
        return y+116
    def field(self,x,y,w,k,v,highlight=False):
        self.text(x,y,k,25,C['sub']);self.text(x+w-20-self.d.textlength(v,font=self.font(27)),y,v,27,C['red'] if highlight else C['ink'])
        self.line(x,y+48,x+w,y+48)
        return y+68
    def save(self,name):
        self.im.save(OUT/name,optimize=True)
        thumb=self.im.copy();thumb.thumbnail((1200,1800));thumb.save(OUT/(name.replace('.png','-preview.png')))

def overview():
    p=Poster(3530,'01','生意账本 · 页面与完整经营流程','四个 Tab：看经营 → 管客户 → 查账单 → 管资料与报表。一个登录账号，就是一本生意总账。')
    p.section(250,'A','四个主页面','首页与账单取流程第4步；客户列表另展示多种余额状态。沿用米色导航、珊瑚红主色与蓝色负余额。')
    xs=[65,645,1225,1805]; fw=530; fy=360; fh=1020
    y=p.phone(xs[0],fy,fw,fh,'首页',0);x=xs[0]
    p.pill(x+25,y,'2026 秋季',C['greenbg'],C['green'],230);y+=80
    for a,(lab,val,col) in enumerate([('当前待收','¥500.00','red'),('当前待付','¥1,800.00','blue'),('本季销售收入','¥1,000.00','ink'),('本季经营利润','¥200.00','green')]):
        xx=x+25+(a%2)*245; yy=y+(a//2)*140
        p.rect(xx,yy,235,120,C['bg'],14);p.text(xx+15,yy+15,lab,24,C['sub']);p.text(xx+15,yy+58,val,30,C[col],True)
    y+=310;p.text(x+25,y,'库存概况',29,bold=True);y+=60
    y=p.field(x+25,y,480,'尿素 · 50kg/袋','10 袋');y=p.field(x+25,y,480,'小麦','1,000 斤 / 0.5 吨')
    y+=12;p.text(x+25,y,'快捷记一笔',29,bold=True);y+=60
    for a,s in enumerate(['卖化肥','收粮','化肥进货','卖粮','收款','付款','抵账','费用']):
        xx=x+25+(a%4)*123;yy=y+(a//4)*83
        p.rect(xx,yy,111,65,C['redbg'] if a<4 else C['bluebg'],12);p.text(xx+10,yy+18,s,23,C['red'] if a<4 else C['blue'])
    x=xs[1];y=p.phone(x,fy,fw,fh,'客户',1)
    p.pill(x+25,y,'农户客户',C['redbg'],C['red'],220);p.pill(x+265,y,'商家客户',C['bg'],C['sub'],230);y+=75
    p.rect(x+25,y,480,60,C['bg'],14);p.text(x+45,y+14,'搜索姓名 / 称谓',25,C['sub']);y+=100
    for name,net,sub,col in [('张三（大叔）','−¥700.00','化肥 ＋500  |  粮食 −1,200','blue'),('李四（表哥）','＋¥1,200.00','化肥 ＋1,200  |  粮食 0','red'),('王五（邻居）','待抵账','化肥 ＋600  |  粮食 −600','purple'),('赵六','已结清','化肥 0  |  粮食 0','sub')]:
        p.d.ellipse((x+25,y+4,x+81,y+60),fill=C['beige']);p.text(x+40,y+14,name[0],26,C['sub'])
        p.text(x+98,y,name,27,bold=True);p.text(x+500-p.d.textlength(net,font=p.font(27)),y,net,27,C[col])
        p.text(x+98,y+49,sub,23,C['sub']);p.line(x+25,y+102,x+505,y+102);y+=137
    p.d.ellipse((x+422,fy+fh-185,x+492,fy+fh-115),fill=C['red']);p.text(x+441,fy+fh-181,'＋',42,C['paper'])
    x=xs[2];y=p.phone(x,fy,fw,fh,'账单',2)
    p.pill(x+25,y,'全部',C['redbg'],C['red'],105);p.text(x+150,y+6,'买卖   收付   抵账   费用',26,C['sub']);y+=80
    p.text(x+25,y,'2026-10 ▾    全部客户 ▾    筛选',25,C['sub']);y+=75
    records=[('10-28  销售 · 尿素10袋','张三 · 原单 ¥1,000','剩余应收 ¥500','red'),('10-28  收款 · 张三','实收 ¥500 · 化肥款','已关联销售原单','green'),('10-28  收粮 · 小麦1000斤','张三 · 原单 ¥1,200','剩余应付 ¥1,200','blue'),('10-27  进货 · 尿素1吨','李老板 · 原单 ¥1,600','剩余应付 ¥600','blue')]
    for title,sub,val,col in records:
        p.text(x+25,y,title,27,bold=True);p.text(x+25,y+48,sub,25,C['sub']);p.text(x+25,y+92,val,25,C[col]);p.line(x+25,y+143,x+505,y+143);y+=175
    x=xs[3];y=p.phone(x,fy,fw,fh,'我的',3)
    for title,items in [('商品资料',['商品分类','商品与规格','常用价格']),('库存与资金',['库存明细','期初库存','资金账户']),('经营与报表',['经营季','经营报表','经营费用']),('账户与设置',['登录资料','生意设置','切换入口'])]:
        p.text(x+25,y,title,29,bold=True);y+=55
        for a,s in enumerate(items):
            xx=x+25+a*163;p.rect(xx,y,150,82,C['bg'],12);p.text(xx+13,y+27,s,24)
        y+=127
    p.text(x+25,fy+fh-158,'商品、资金、库存与报表为待开发',23,C['sub'])
    p.text(x+25,fy+fh-122,'无创建账套 / 无生意成员管理',23,C['sub'])
    p.section(1435,'B','业务影响矩阵','买卖形成货物流与债权债务；结算冲减具体欠款。收款、利润、抵账是不同事实。')
    cols=[65,445,785,1125,1465,1890,2335];row=78;top=1540
    heads=['操作','库存','应收 / 应付','现金','收入 / 利润','记录来源']
    for i,s in enumerate(heads): p.text(cols[i]+15,top+15,s,27,bold=True)
    rows=[['买入（进肥 / 收粮）','增加','形成应付','按实付减少','先保留库存成本','买卖原单'],['卖出（卖肥 / 卖粮）','减少','形成应收','按实收增加','销售 − 对应成本','买卖原单'],['收款 / 付款','不变','分配冲减','按实际变动','不重复计收入','结算 + 分配'],['确认抵账','不变','两侧等额冲减','不变','不生成新利润','抵账 + 两侧分配'],['经营费用','不变','按首版现付设计','减少','减少当期利润','费用记录']]
    for j,vals in enumerate(rows):
        yy=top+(j+1)*row;p.d.rectangle((65,yy,2335,yy+row),fill=C['paper'] if j%2==0 else C['bg'])
        for i,s in enumerate(vals):p.text(cols[i]+15,yy+18,s,25,C['sub'] if i else C['ink'])
    p.section(2070,'C','同一组数字，走完进货 → 结款 → 利润','期初资金 ¥10,000；所有金额均为示例。以下卡片依次发生，未列出的其他客户欠款仍独立保留。')
    steps=[('01 化肥进货',['李老板 → 我们','1吨 = 20袋 × 50kg','20袋 × ¥80 = ¥1,600','实付 ¥1,000，欠 ¥600','库存20袋｜资金9,000']),('02 卖化肥',['我们 → 张三','10袋 × ¥100 = ¥1,000','未收款，应收 +1,000','成本800，毛利200','库存10袋｜资金9,000']),('03 收回部分欠款',['张三实际给 ¥500','关联化肥原单 ¥500','化肥未结 +500','原始销售仍是1,000','库存不变｜资金9,500']),('04 收购小麦',['张三 → 我们','1000斤 × ¥1.2 = ¥1,200','未付款，应付 −1,200','张三净额 −700','小麦1000斤｜资金9,500']),('05 确认抵账',['化肥应收与粮款互抵500','两侧分别核销 ¥500','化肥0，粮款 −700','不生成现金收支','库存不变｜资金9,500']),('06 付清张三',['实际付出 ¥700','分配至剩余粮款','化肥0，粮款0','张三才是全部结清','库存不变｜资金8,800']),('07 卖粮并收齐',['1000斤 = 0.5吨','0.5吨 × ¥2600 = ¥1,300','成本1,200，毛利100','当场收款 ¥1,300','小麦归零｜资金10,100']),('08 记录经营费用',['当期费用实付 ¥100','总销售2,300，毛利300','经营利润 = 300 − 100','仍欠李老板600','剩化肥10袋｜资金10,000'])]
    for j,(title,lines) in enumerate(steps):
        xx=65+(j%4)*580; yy=2180+(j//4)*445
        p.card(xx,yy,530,395,title,lines,accent='purple' if j==4 else 'green',size=27)
        if j%4<3:p.arrow(xx+537,yy+190,xx+570,yy+190,C['purple'] if j==4 else C['green'])
    p.card(65,3100,1110,255,'最后能看到什么',['张三已结清；供应商仍有应付600；化肥库存成本800。','销售2,300 − 已售成本2,000 − 费用100 = 利润200。','现金净变动0；仍可盈利200，现金与利润不能混用。'],size=27)
    p.card(1225,3100,1110,255,'本期真实完成到哪里',['已有：客户档案接口、当前未结款计算、独立账号归属。','待开发：买卖录入、收付款、抵账、商品、库存、费用和报表。','待定：成本算法、退货/损耗、期初历史欠款、封账规则。'],accent='blue',size=27)
    p.save('01-overview.png')

def pages():
    p=Poster(3550,'02','生意账本 · 关键页面与表单','日常尽量点选：客户 → 商品 → 数量 → 收付状态 → 保存。需要追溯时，再展开原单与结算分配。')
    p.section(250,'A','八张关键录入页面','客户表单已有接口；其余为后续功能设计。录入金额均由服务端核算，页面不直接设置客户余额。')
    panels=[
      ('新增 / 修改客户','已有基础',[('客户类型','农户 / 商家'),('姓名 / 名称 *','张三'),('称谓','大叔'),('电话','选填'),('备注','选填')],'姓名必填；称谓自由输入。','保存客户'),
      ('商品与规格','待开发',[('分类 / 商品','化肥 / 尿素'),('规格 *','50 kg / 袋'),('基础重量','千克'),('常用进价','80 元 / 袋'),('常用售价','100 元 / 袋')],'成交时可改价；旧单保留快照。','保存商品'),
      ('化肥进货','待开发',[('供货客户','李老板 · 商家'),('商品','尿素 / 50kg'),('数量 / 单位','1 吨 → 20 袋'),('成交金额','¥1,600.00'),('实付 / 未付','1,000 / 600')],'原单1,600；另存付款1,000。','保存进货'),
      ('卖化肥','待开发',[('客户','张三（大叔）'),('商品 / 单价','尿素 / 100元每袋'),('数量','10 袋'),('本单金额','¥1,000.00'),('收款状态','未收 / 部分 / 收齐')],'可加多种产品；常用价自动带出。','保存销售'),
      ('收粮','待开发',[('卖粮客户','张三（大叔）'),('品种','小麦'),('重量','1,000 斤 = 500kg'),('单价 / 金额','1.2元 / 1,200元'),('付款状态','未付 / 部分 / 付清')],'先保留应付；以后可抵化肥款。','保存收粮'),
      ('卖粮','待开发',[('收粮商','粮商王老板'),('品种 / 数量','小麦 / 0.5 吨'),('成交单价','2,600 元 / 吨'),('本单金额','¥1,300.00'),('实收 / 账户','1,300 / 生意现金')],'库存扣500kg；成本按选定算法。','保存卖粮'),
      ('收款 / 付款','待开发',[('客户 / 方向','张三 / 收款'),('实际金额','¥500.00'),('资金账户 *','生意现金'),('实际日期','2026-10-28'),('核销方式','先旧后新 / 指定单')],'展开可查本次分配到哪些原单。','确认收款'),
      ('抵账并付清','待开发',[('客户','张三（大叔）'),('未结应收','化肥 500'),('未结应付','粮食 1,200'),('确认抵账','¥500.00'),('再实际付款','¥700.00')],'抵账500与付款700分别留痕。','确认抵账并付款')]
    for j,(title,status,fields,note,btn) in enumerate(panels):
        x=65+j%4*580;y=360+j//4*820;w=530;h=760
        p.rect(x,y,w,h,C['paper'],26);p.text(x+25,y+26,title,33,bold=True)
        p.pill(x+25,y+83,status,C['greenbg'] if status=='已有基础' else C['bluebg'],C['green'] if status=='已有基础' else C['blue'])
        yy=y+160
        for k,v in fields:yy=p.field(x+25,yy,480,k,v)
        p.para(x+25,y+540,note,480,25,C['sub'])
        p.rect(x+25,y+650,480,75,C['red'],14,C['red']);p.text(x+48,y+670,btn,29,C['paper'])
    p.section(2040,'B','客户详情 → 买卖原单 → 结算分配','这里用“收款500后、尚未抵账”的张三状态；每笔未结金额都能回到产生它的原始明细。')
    p.card(65,2150,725,610,'客户详情 · 张三（大叔）',[
        '当前净余额 −700；化肥 +500 / 粮食 −1,200。',
        '分别显示：应收500；应付1,200。',
        '可查：买卖 / 收付 / 抵账 / 各季往来。',
        '快捷：卖货、收粮、收款、付款、抵账。',
        '资料修改；删除需应收应付分别为0。',
        '净额0而双向未结：显示待抵账。',
        '本季往来与往季未结分别筛选。'],size=29)
    p.card(835,2150,725,610,'买卖原单 · 化肥销售',[
        '销售原金额 ¥1,000，不因还款改写。',
        '尿素10袋 × ¥100；每袋50kg快照。',
        '已结 ¥500；未结应收 ¥500。',
        '可展开：10-28 收款500 → 这笔明细。',
        '保留业务日期、原经营季、商品快照。',
        '关联成本800与库存出库500kg。',
        '改错需校验结算/库存依赖并保留历史。'],accent='blue',size=29)
    p.card(1605,2150,730,610,'结算详情 · 抵账500',[
        '抵账单金额 ¥500；现金变动 ¥0。',
        '核销应收：化肥原单500。',
        '核销应付：粮食原单500。',
        '两侧各500，不能合计为抵账1,000。',
        '抵后化肥0，粮食仍应付700。',
        '再付款700形成独立付款及分配记录。',
        '组合保存失败：抵账与付款全部回滚。'],accent='purple',size=29)
    p.arrow(798,2440,825,2440);p.arrow(1568,2440,1595,2440)
    p.section(2820,'C','必须同时画出的界面状态')
    p.card(65,2910,725,440,'快速录入与成功状态',[
        '从客户详情进入，预选当前客户。',
        '多商品一单；成交价可调整。',
        '保存成功：继续记当前客户 / 换人。',
        '真实列表刷新后回显未结金额。',
        '无数据：0金额 + 对应新增入口。'],size=29)
    p.card(835,2910,725,440,'失败与并发状态',[
        '加载失败保留筛选，允许重试。',
        '保存失败保留所填内容。',
        '重复请求不重复入账或扣库存。',
        '欠款已变化：重新显示可结金额。',
        '超额核销、无效规格明确提示。'],accent='blue',size=29)
    p.card(1605,2910,730,440,'边界与待定扩展',[
        '先确认再抵账，不自动抹平双向款。',
        '不让用户输入owner_id或改余额。',
        '司机不默认作为交易和结款客户。',
        '粮食水分/扣杂、优惠和抹零待定。',
        '预收预付、退货、导出另定范围。'],accent='purple',size=29)
    p.save('02-pages.png')

def rules():
    p=Poster(3370,'03','生意账本 · 库存、利润与跨季追溯','一笔买卖保存原貌；每次结款记录去向；库存、资金、利润分别核算，跨季仍能追到同一张原单。')
    p.section(250,'A','计量与利润口径','成本算法尚未确定：先进先出 / 移动加权平均需开发前选定。本示例每种商品仅一批，不预设算法。')
    p.card(65,360,725,430,'化肥：按吨进，按袋卖',[
        '1吨 = 1,000kg；每袋50kg → 20袋。',
        '进20袋×80 = 库存成本1,600。',
        '卖10袋×100 = 销售收入1,000。',
        '已售成本800；毛利200。',
        '剩10袋 / 500kg，库存成本800。'],size=29)
    p.card(835,360,725,430,'粮食：按斤收，按吨卖',[
        '1市斤 = 0.5kg；1000斤 = 0.5吨。',
        '收1000斤×1.2 = 库存成本1,200。',
        '卖0.5吨×2,600 = 收入1,300。',
        '已售成本1,200；毛利100。',
        '原始数量、单位与换算都留痕。'],accent='blue',size=29)
    p.card(1605,360,730,430,'经营利润与现金分开',[
        '销售收入 = 1,000 + 1,300 = 2,300。',
        '销售毛利 = 2,300 − 2,000 = 300。',
        '经营利润 = 300 − 当期费用100 = 200。',
        '本例现金净变动0，不代表利润0。',
        '旧欠款回收与抵账不重复计收入。'],accent='green',size=29)
    p.section(845,'B','跨季只延续历史，不复制新欠款')
    p.card(65,945,710,440,'2026 麦季 · 历史时点',[
        '假设季末化肥未收1,000。',
        '保留当季销售原单及未结明细。',
        '历史余额只计算截止当时的结算。',
        '库存数量与成本由历史变动形成。',
        '期初录入不伪造本期收入/采购。'],size=29)
    p.card(845,945,710,440,'2026 秋季 · 继续经营',[
        '秋季收500，分配到麦季原单。',
        '当前剩500；麦季末当时仍为1,000。',
        '500计秋季现金，不计秋季销售。',
        '存货沿用数量与成本来源。',
        '上季欠款不再新建一次。'],accent='purple',size=29)
    p.card(1625,945,710,440,'封账与补录 · 待定',[
        '不把“新经营季”当作“新账套”。',
        '是否封账、补录、撤销生效日待定。',
        '历史查询不等于已建立封账快照。',
        '商品改价不改旧单成交价与规格。',
        '期初历史债务的迁入方式另定。'],accent='blue',size=29)
    p.arrow(783,1150,835,1150,C['purple']);p.arrow(1563,1150,1615,1150,C['purple'])
    p.section(1440,'C','生意域内的数据关系与写入边界','实线关系表示业务关联；商品、资金、库存及费用的具体表结构在对应阶段补齐。所有记录均按账号归属。')
    p.card(65,1550,500,340,'客户 biz_customer',[
        '已有档案与余额查询。',
        '一个客户可同时买/卖。',
        'owner_id 来自登录Token。'],size=28)
    p.card(665,1550,720,340,'买卖 → 买卖商品明细',[
        'biz_trade  1 → N  biz_trade_item。',
        '原金额、方向、季、单位与规格快照。',
        '形成应收/应付；后续接库存与成本。'],accent='blue',size=28)
    p.card(1485,1550,850,340,'结算 → 结算分配 → 原商品明细',[
        'biz_settlement → biz_settlement_item。',
        '一次结多笔；一笔分多次；跨季仍指原明细。',
        '收/付款记现金；抵账两侧等额、不记现金。'],accent='purple',size=28)
    p.arrow(575,1700,655,1700);p.arrow(1475,1740,1395,1740,C['purple'])
    p.card(65,1930,1110,320,'后续补齐的生意能力',[
        '商品分类/商品；生意资金账户/流水；库存变动/成本来源；费用。',
        '买卖、结算、库存与资金同事务，不能保存一半。',
        '经营季可先作标签；是否独立建表按需求确认。'],size=29)
    p.card(1225,1930,1110,320,'防错约束与真实验收',[
        '金额整数分、精确数量单价；幂等、并发锁、失败全回滚。',
        '校验同一账号/客户、可结金额、两侧抵账平衡及库存。',
        '已建查询表 ≠ 已实现录入；真实PG与微信真机仍需验收。'],accent='blue',size=29)
    p.section(2310,'D','两套业务隔离，按阶段落地')
    p.card(65,2410,1110,360,'长期边界 · 已确认',[
        '生意：business schema / biz_ 表 / /business/**。',
        'business-service / com.base.business；一个账号一本总账。',
        '家庭表、接口、Service、资金及统计独立；无自动同步。',
        '只共用登录及技术基础；停用生意不牵动家庭。'],size=29)
    p.card(1225,2410,1110,360,'已有基础与范围',[
        '客户5个接口、资料/当前未结款查询、归属和删除约束。',
        '买卖与结算表已有查询基础，尚无对应录入接口。',
        '新增迁移按现有构建方式登记；已执行迁移不改删。',
        '图中规划不作为已开发、已上线或验收通过的证据。'],accent='blue',size=29)
    phases=[('先做资料','客户样式验收\n商品 / 单位 / 规格'),('打通往来','卖肥 / 收粮 / 收付\n抵账 / 明细 / 对账'),('补齐进销存','进肥 / 卖粮 / 期初\n库存 / 成本算法'),('完成经营汇总','费用 / 季 / 历史\n利润 / 现金 / 首页')]
    for i,(title,body) in enumerate(phases):
        x=65+i*580;p.card(x,2830,530,240,title,[body],size=29)
        if i<3:p.arrow(x+537,2945,x+570,2945)
    p.text(65,3130,'扩展待定：退货、损耗、盘点、质量扣量、抹零、预收预付、封账、导出备份。',29,C['sub'])
    p.text(65,3190,'每期都验收：账号隔离、部分结款、抵账无现金、净额0待抵账、并发回滚与跨季追溯。',29,C['sub'])
    p.save('03-rules.png')

if __name__=='__main__':
    overview(); pages(); rules()
    print('Rendered three design posters and previews to', OUT)
