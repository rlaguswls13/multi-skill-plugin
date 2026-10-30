# skill-color-code-converter

HEX(`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`), RGB, RGBA, HSL, CMYK 색상 모델 상호 변환 및 W3C WCAG 2.1 상대 명도(Relative Luminance) 기반 텍스트-배경 대비비(Contrast Ratio)와 AA(4.5:1), AAA(7.0:1) 접근성 적합성을 검증하는 단일 기술 모듈입니다.

## 주요 기능
- `Color.fromHex("#336699")`: HEX 문자열 파싱
- `color.toHsl()` / `color.toCmyk()`: HSL/CMYK 모델 변환
- `color.contrastRatio(background)`: 1.0 ~ 21.0:1 명도 대비율 계산
- `color.isWcagAa(background)`: WCAG 2.1 AA (4.5:1 이상) 적합성 검증
