package sisaku;

public class Drone {

	final static int EDGE_SERVER = 60000;

	final static int WAIT = 0;//初期
	final static int GO = 1;//移動
	final static int SENSING = 2;//通常状態
	final static int GATHERING = 3;//招集
	final static int BACK = 4;//帰還
	final static int END = 5;//終了

	final static short NULL = 0;
	final static short NORTH = 1;
	final static short EAST = 2;
	final static short SOUTH = 3;
	final static short WEST = 4;

	final static int gNULL = 0;
	final static int gWAIT = 1;
	final static int gSTANDBY = 2;
	final static int gSENSING = 3;
	final static int gBACK = 4;

	final static double CONSUMPTION = 0.06;//1秒間の電池消費

	int id;//ドローンのID
	int mCastID;
	double x,y,z;
	double battery;//バッテリー
	int state;//ドローンの状態
	int gatheringState;
	//int area;
	int initX, initY;;//初期値
	int oneBlock;//1区画
	int discover;//データの格納
	String message;

	short direction;//方向
	double speed;//スピード
	double firstMove;//初期移動
	double arrivalTime;//到着時間
	double lapseTime;//経過時間

	Udp udp;
	UdpSecond udps;
	mCastUdp mcastudp;


	Drone(int id, int initX, int initY){//コンストラクタ
		this.id = id;
		this.initX = initX;
		this.initY = initY;
		//this.area = area;
		x = 0.0;
		y = 0.0;
		battery = 100.0;
		state = WAIT;
		gatheringState = gNULL;
		direction = NULL;
		speed = 10;
		firstMove = Math.sqrt(Math.pow(initX - x, 2) + Math.pow(initY - y, 2));
		arrivalTime = firstMove / speed;
		lapseTime = 0.0;
		oneBlock = 30;
		message = "Normal ";
		mCastID = 4000;

		udp = new Udp(id);
		udp.makeMulticastSocket() ;//ソケット生成
		udp.startListener() ;//受信

		udps = new UdpSecond(id);
		udps.makeMulticastSocket() ;//ソケット生成
		udps.startListener() ;//受信

		mcastudp = new mCastUdp(mCastID);
		mcastudp.makeMulticastSocket() ;
		mcastudp.startListener();
	}

	void move(double simTime) {//移動メソッド

		if(state != END) {
			battery -= CONSUMPTION * simTime;
		}

		if(battery < 50.0) {
			message = "Lack ";
		}


		lapseTime += simTime;


		udps.sendData(id, x, y, battery, EDGE_SERVER);
		udps.lisnersecond.resetData();


		if(battery < 10.0) state = BACK;//10%以下で帰還

		switch(state) {

		case WAIT:
			 state = GO;
			 break;
		case GO:
			double goTheta = Math.atan2(initY, initX);//角度
			double goDistance = speed * simTime;
			x += goDistance * Math.cos(goTheta);
			y += goDistance * Math.sin(goTheta);

			if(lapseTime >= arrivalTime){
				x = initX;
				y = initY;
				lapseTime = 0.0;
				state = SENSING;
				direction = SOUTH;
			}
			break;

		case SENSING:
			message = "Normal ";
			if(lapseTime >= 3.0) {
				switch(direction) {
				case NORTH://上へ
					y += 30;
					if(y >= initY) direction = EAST;
					break;
				case EAST://右へ
					x += 30;
					if(y >= initY) direction = SOUTH;
					else direction = NORTH;
					break;
				case SOUTH://下へ
					y -= 30;
					if(y <= initY - 240) direction = EAST;
					break;

				case WEST: break;//左へ
				default: break;

				}
				byte[] rcvData = udp.lisner.getData();
				if(rcvData != null) {//受信データが空でないのなら
					String str = new String(rcvData,0,1);//byte型を文字に変換(ごみを削除)

					if(str.equals("T")) {
						if(battery >= 50.0) {
							message = "Accept ";
							state = GATHERING;//招集状態へ
							gatheringState = gWAIT;
						}
						else {
							message = "Decline ";
							udp.lisner.resetData();//データのリセット
							state = SENSING;//続行
							gatheringState = gNULL;
							}
						}
					//if(str.equals("F")) {
					//}
					else {
						state = SENSING;//続行
					}

					udp.lisner.resetData();//データのリセット
				}

			lapseTime = 0.0;//経過時間

			}

			if(x >= initX + 240 && y >= initY) {
				state = BACK;
				direction = NULL;
			}

			break;

		case GATHERING:
			switch(gatheringState) {
			case gWAIT:
				byte[] rcvData = udp.lisner.getData();
				if(rcvData != null) {//受信データが空でないのなら
					String str = new String(rcvData,0,18);//byte型を文字に変換(ごみを削除)
					if(str.equals("ProvisionalRequest")) {
						message = "ProvisionalReply ";
					}
					udp.lisner.resetData();//データのリセット

					String str1 = new String(rcvData,0,11);


					if(str1.equals("MainRequest")) {
						message = "MainReply ";
						gatheringState = gSTANDBY;
					}

				}
				break;

			case gSTANDBY:

				break;

			case gSENSING:

				break;

			case gBACK:

				break;

			}

			break;
		case BACK:
			double backTheta = Math.atan2(y, x);
			//lapseTime = 0;
			double backDistance = speed * simTime;
			x -= backDistance * Math.cos(backTheta);
			y -= backDistance * Math.sin(backTheta);
			if(x <= 0 && y <= 0) {
				x = 0;
				y = 0;
				state = END;
				direction = NULL;
			}
			break;
		default:
			break;
		}

	 }



	void dataGet(int[][] area){//データ収集メソッド
		if(state == SENSING || state == GATHERING ) {//通常状態もしくは招集状態
			if(x < 30 && y < 30) {

			}
			else if(x < 30 && y >= 30) {
				discover = area[(int)(x / 30) ][(int)(y / 30) - 1];//データ抽出
			}
			else if(x >= 30 && y < 30) {
				discover = area[(int)(x / 30) - 1][(int)(y / 30) ];//データ抽出
			}
			else {
				discover = area[(int)(x / 30) - 1][(int)(y / 30) - 1];//データ抽出
			}
				udp.sendData(id, message, discover, x, y, battery, EDGE_SERVER);//エッジに送信
				udp.lisner.resetData();//データのリセット


		}
	}


}