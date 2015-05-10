package study.masystems.purchasingsystem;

public class CityPath {
    private Integer start;
    private Integer finish;

    public CityPath() {

    }

    public CityPath(Integer _start, Integer _finish) {
        start = _start;
        finish = _finish;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getFinish() {
        return finish;
    }

    public void setFinish(Integer finish) {
        this.finish = finish;
    }
}
