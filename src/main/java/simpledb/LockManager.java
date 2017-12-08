package simpledb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {

    //Key�൱����Դ��LockState�������id�������ͣ���ÿ��LockState����ĳ������Key�ϼ�����
    //������mapΪ������Դ������Ϣ
    private Map<PageId, List<LockState>> lockStateMap;

    //KeyΪ����PageIdΪ���ڵȴ�����Դ���൱�ڱ����˵ȴ�����Ϣ��PS��BufferPool��ʵ���õ���sleep���ֵȴ�
    private Map<TransactionId, PageId> waitingInfo;

    public LockManager() {
        //ʹ��֧�ֲ�������������ConcurrentModificationException
        lockStateMap = new ConcurrentHashMap<>();
        waitingInfo = new ConcurrentHashMap<>();
    }


//==========================������,����,��������ط��� begin==================================

    /**
     * ���tid�Ѿ���pid���ж���������true
     * ���tid��pid���Ѿ���д��������û��������������tid��pid�Ӷ�����������󷵻�true
     * ���tid��ʱ���ܸ�pid�Ӷ���������false
     *
     * @param tid
     * @param pid
     * @return
     */
    public synchronized boolean grantSLock(TransactionId tid, PageId pid) {
        ArrayList<LockState> list = (ArrayList<LockState>) lockStateMap.get(pid);
        if (list != null && list.size() != 0) {
            if (list.size() == 1) {//pid��ֻ��һ����
                LockState ls = list.iterator().next();
                if (ls.getTid().equals(tid)) {//�ж��Ƿ�Ϊ�Լ�����
                    //����Ƕ�����ֱ�ӷ��أ���||��֮ǰ���أ�����������ٷ���
                    return ls.getPerm() == Permissions.READ_ONLY || lock(pid, tid, Permissions.READ_ONLY);
                } else {
                    //����Ǳ��˵Ķ����������ٷ��أ���д������Ҫ�ȴ�
                    return ls.getPerm() == Permissions.READ_ONLY ? lock(pid, tid, Permissions.READ_ONLY) : wait(tid, pid);
                }
            } else {
                //��������������
                // 1.���������Ҷ�����tid��һ��һд��    2.���������Ҷ����ڷ�tid������һ��һд��
                // 3.�����������������һ��Ϊtid�Ķ���  4.�����������û��tid�Ķ���
                for (LockState ls : list) {
                    if (ls.getPerm() == Permissions.READ_WRITE) {
                        //���������һ��д������ô�����Ƿ�Ϊ�Լ������ж��������1����2
                        return ls.getTid().equals(tid) || wait(tid, pid);
                    } else if (ls.getTid().equals(tid)) {//����Ƕ�������tid��
                        return true;//���3�ڴ˷��أ�Ҳ���������1������ȱ�����������
                    }
                }
                //���4
                return lock(pid, tid, Permissions.READ_ONLY);
            }
        } else {
            return lock(pid, tid, Permissions.READ_ONLY);
        }
    }

    /**
     * ���tid�Ѿ���pid����д�����򷵻�true
     * �����tidӵ��pid�Ķ�������tid��pid��û��������������tid��pid��д����������󷵻�true
     * ���tid��ʱ���ܸ�pid��д��������false
     *
     * @param tid
     * @param pid
     * @return
     */
    public synchronized boolean grantXLock(TransactionId tid, PageId pid) {
        ArrayList<LockState> list = (ArrayList<LockState>) lockStateMap.get(pid);
        if (list != null && list.size() != 0) {
            if (list.size() == 1) {//���pid��ֻ��һ����
                LockState ls = list.iterator().next();
                //������Լ���д����ֱ�ӷ��أ���||֮ǰ���أ�����������ٷ��أ���lock�����أ�
                //���������Ǳ��˵ģ�����ȴ���Ҳ������wait����ð��֮�󣩷���
                return ls.getTid().equals(tid) ? ls.getPerm() == Permissions.READ_WRITE || lock(pid, tid, Permissions.READ_WRITE) : wait(tid, pid);
            } else {
                //����������������ֻ�е�һ���������true�����෵��wait
                // 1.���������Ҷ�����tid��һ��һд�� 2.���������Ҷ����ڷ�tid������һ��һд�� 3.�������
                if (list.size() == 2) {
                    for (LockState ls : list) {
                        if (ls.getTid().equals(tid) && ls.getPerm() == Permissions.READ_WRITE) {
                            return true;//������������һ���Լ���д��
                        }
                    }
                }
                return wait(tid, pid);
            }
        } else {//pid��û���������Լ�д��
            return lock(pid, tid, Permissions.READ_WRITE);
        }
    }


    /**
     * ��������ʾtid��pid����һ��permȨ�޵�����������true
     * @param pid
     * @param tid
     * @param perm
     */
    private synchronized boolean lock(PageId pid, TransactionId tid, Permissions perm) {
        LockState nls = new LockState(tid, perm);
        ArrayList<LockState> list = (ArrayList<LockState>) lockStateMap.get(pid);
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(nls);
        lockStateMap.put(pid, list);
        waitingInfo.remove(tid);
        return true;
    }

    /**
     * ֻ�Ǵ����waitingInfo����ϢȻ�󷵻�false
     * @param tid
     * @param pid
     * @return
     */
    private synchronized boolean wait(TransactionId tid, PageId pid) {
        waitingInfo.put(tid, pid);
        return false;
    }


    /**
     * unlock�����Ϊ������ʱ���ã�����������򷵻�false
     * �����������Ƿ���ڵĴ����Ѿ��ڷ����ڣ��������ط�������ȷ�ϴ�����unlock
     * ����Ӧ����unlock�ٸ��ݷ��ؽ���ж��Ƿ����
     *
     * @param tid
     * @param pid
     * @return
     */
    public synchronized boolean unlock(TransactionId tid, PageId pid) {
        ArrayList<LockState> list = (ArrayList<LockState>) lockStateMap.get(pid);

        if (list == null || list.size() == 0) return false;
        LockState ls = getLockState(tid, pid);
        if (ls == null) return false;
        list.remove(ls);
        lockStateMap.put(pid, list);
        return true;
    }

    /**
     * �ͷ�����tidӵ�е�������
     *
     * @param tid
     */
    public synchronized void releaseTransactionLocks(TransactionId tid) {
        //���ҳ����У����ͷ�
        List<PageId> toRelease = getAllLocksByTid(tid);
        for (PageId pid : toRelease) {
            unlock(tid, pid);
        }
    }

//==========================������,����,��������ط��� end==================================


//==========================�����������ط��� beign======================================

    /**
     *
     * ͨ�������Դ������ͼ�����Ƿ���ڻ����ж��Ƿ��Ѿ���������
     * ����ʵ�֣�������tid��Ҫ��⡰���ڵȴ�����Դ��ӵ�����Ƿ��Ѿ�ֱ�ӻ��ӵ��ڵȴ�������tid�Ѿ�ӵ�е���Դ��
     * <p>
     * ��ͼ��������P1,P2,P3Ϊ��Դ,T1,T2,T3Ϊ����
     * �����Լ����ϵ���ĸR���ϼ�ͷ�����ӵ�й�ϵ���������ĸW��������ڵȴ�д��
     * ������ͼ���Ϸ�T1��P1��һ�������ű�ʾ����T1��ʱӵ��P1�Ķ���
     * ͼ�ı�Ե���������ߵ�ת�۵㣬����Ϊ�˱�ʾT2���ڵȴ�P1
     * <p>
     * //     T1---R-->P1<-------
     * //                       W
     * //  ----------------------
     * //  W
     * //  ---T2---R-->P2<-------
     * //                       W
     * //  ----------------------
     * //  W
     * //  ---T3---R-->P3
     * <p>
     * ��ͼ�ĺ����ǣ�Tiӵ���˶�Pi�Ķ���(1<=i<=3)
     * ��ΪT1��P1�����˶���������T2���ڵȴ�P1��д��
     * ͬ��T3���ڵȴ�P2��д��
     * <p>
     * ���ڼ�����龰�ǣ���ʱT1Ҫ�����P3��д��������ȴ����⽫���������
     * ��������������������жϣ��Ϳ��Ե�֪�Ѿ����������Ӷ��ع����񣨾�����BufferPool��getPage()������whileѭ����ʼ����
     * <p>
     * ���������ı���ԭ����ǽ��ȴ�����Դ(P3)��ӵ����(T3)��ӵ��ڵȴ�T1ӵ�е���Դ(P1)
     * ���淽����ע�����������Ϊ��������������������������жϳ���T1��P3�ϵĵȴ��Ѿ��������������
     *
     * @param tid
     * @param pid
     * @return true��ʾ������������false��ʾû��
     */
    public synchronized boolean deadlockOccurred(TransactionId tid, PageId pid) {//T1Ϊtid��P3Ϊpid
        List<LockState> holders = lockStateMap.get(pid);
        if (holders == null || holders.size() == 0) {
            return false;
        }
        List<PageId> pids = getAllLocksByTid(tid);//�ҳ�T1ӵ�е�������Դ����ֻ����P1��list
        for (LockState ls : holders) {
            TransactionId holder = ls.getTid();
            //ȥ��T1����Ϊ��Ȼ��ͼû���������������T1����ͬʱҲ������Page���ж��������Ӱ���жϽ��
            if (!holder.equals(tid)) {
                //�ж�T3(holder)�Ƿ�ֱ�ӻ����ڵȴ�P1(pids)
                //��ͼ���Կ���T3��ֱ�ӵȴ�P2����P2��ӵ����T2��ֱ�ӵȴ�P1,��T3�ڼ�ӵȴ�P1
                boolean isWaiting = isWaitingResources(holder, pids, tid);
                if (isWaiting) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * �ж�tid�Ƿ�ֱ�ӻ��ӵ��ڵȴ�pids�е�ĳ����Դ
     *
     * @param tid
     * @param pids
     * @param toRemove ��Ҫ�ų�toRemove���жϣ�����ԭ��������ڲ�ע�ͣ�
     *                 ��ʵ�ϣ�toRemove����leadToDeadLock()�Ĳ���tid��Ҳ����Ҫ�ų����Լ����жϹ��̵�Ӱ��
     * @return
     */
    private synchronized boolean isWaitingResources(TransactionId tid, List<PageId> pids, TransactionId toRemove) {
        PageId waitingPage = waitingInfo.get(tid);
        if (waitingPage == null) {
            return false;
        }
        for (PageId pid : pids) {
            if (pid.equals(waitingPage)) {
                return true;
            }
        }
        //��������˵��tid����ֱ���ڵȴ�pids�е�����һ�������п��ܼ���ڵȴ�
        //���waitingPage��ӵ������(ȥ��toRemove)�е�ĳһ�����ڵȴ�pids�е�ĳһ����˵����tid����ڵȴ�
        List<LockState> holders = lockStateMap.get(waitingPage);
        if (holders == null || holders.size() == 0) return false;//����Դû��ӵ����
        for (LockState ls : holders) {
            TransactionId holder = ls.getTid();
            if (!holder.equals(toRemove)) {//ȥ��toRemove����toRemove�պ�ӵ��waitingResource�Ķ���ʱ����Ҫ
                boolean isWaiting = isWaitingResources(holder, pids, toRemove);
                if (isWaiting) return true;
            }
        }
        //�����forѭ����û��return��˵��ÿһ��holder����ֱ�ӻ��ӵȴ�pids
        //��tidҲ�Ǽ�ӵȴ�pids
        return false;
    }

//==========================�����������ط��� end======================================


//==========================��ѯ���޸�����map��Ϣ����ط��� beign=========================

    /**
     * @param tid ʩ����������id
     * @param pid ��������page
     * @return tid�����������pid�ϵ���;��������ڸ���������null
     */
    public synchronized LockState getLockState(TransactionId tid, PageId pid) {
        ArrayList<LockState> list = (ArrayList<LockState>) lockStateMap.get(pid);
        if (list == null || list.size() == 0) {
            return null;
        }
        for (LockState ls : list) {
            if (ls.getTid().equals(tid)) {//�ҵ��˶�Ӧ����
                return ls;
            }
        }
        return null;
    }

    /**
     * �õ�tid��ӵ�е����������������ڵ���Դpid����ʽ����
     *
     * @param tid
     * @return
     */
    private synchronized List<PageId> getAllLocksByTid(TransactionId tid) {
        ArrayList<PageId> pids = new ArrayList<>();
        for (Map.Entry<PageId, List<LockState>> entry : lockStateMap.entrySet()) {
            for (LockState ls : entry.getValue()) {
                if (ls.getTid().equals(tid)) {
                    pids.add(entry.getKey());
                }
            }
        }
        return pids;
    }

//==========================��ѯ���޸�����map��Ϣ����ط��� end=========================

}