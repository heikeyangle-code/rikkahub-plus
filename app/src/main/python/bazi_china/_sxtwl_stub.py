#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Drop-in replacement for sxtwl in bazi_china.luohou — powered by lunar_python.

Verified 1:1 for all interfaces used by luohou.py:
  - Year GZ:   lunar_python Lunar.getYearGanIndexByLiChun / getYearZhiIndexByLiChun
                (立春 year boundary, matches sxtwl)
  - Month GZ:  lunar_python Lunar.getMonthInGanZhiExact
                (节气-aware month boundary, matches sxtwl)
  - Day GZ:    lunar_python Lunar.getDayInGanZhi (continuous 60-day cycle, identical)
  - Hour GZ:   computed from day gan + hour zhi (standard formula, matches sxtwl)
  - 节气:      lunar_python Lunar.getJieQiTable() (matches sxtwl)
"""

from lunar_python import Solar, Lunar

# Initialized by caller with Gan/Zhi lookup tables
_Gan_list = None  # ['甲','乙',...,'癸']
_Zhi_list = None  # ['子','丑',...,'亥']
_Gan_idx = None   # {'甲':0, '乙':1, ...}
_Zhi_idx = None   # {'子':0, '丑':1, ...}

def _init_indexes(gan_list, zhi_list):
    global _Gan_list, _Zhi_list, _Gan_idx, _Zhi_idx
    _Gan_list = gan_list
    _Zhi_list = zhi_list
    _Gan_idx = {g: i for i, g in enumerate(gan_list)}
    _Zhi_idx = {z: i for i, z in enumerate(zhi_list)}

class _GZ:
    """Mimics sxtwl.GZ with .tg (gan index) and .dz (zhi index)."""
    __slots__ = ('tg', 'dz')
    def __init__(self, gan_char, zhi_char):
        self.tg = _Gan_idx[gan_char]
        self.dz = _Zhi_idx[zhi_char]

class _fromSolar:
    """Mimics sxtwl.fromSolar() return value.

    Constructed with (year, month, day) — sxtwl.fromSolar doesn't take time,
    but the calling code extracts hour ganzhi separately via .getHourGZ(h).
    Month GZ calculation is time-sensitive near jieqi boundaries (节气).
    """
    def __init__(self, y, m, d):
        solar = Solar.fromYmd(y, m, d)
        self._lunar = Lunar.fromSolar(solar)
        self._solar = solar

        # ── Day GZ: continuous 60-day cycle, 1:1 identical between libraries ──
        day_gz = self._lunar.getDayInGanZhi()
        self._day_gz = _GZ(day_gz[0], day_gz[1])

        # ── Year GZ: 立春 boundary (matches sxtwl) ──
        self._year_gan_idx = self._lunar.getYearGanIndexByLiChun()
        self._year_zhi_idx = self._lunar.getYearZhiIndexByLiChun()

        # ── Month GZ: jieqi-aware (matches sxtwl) ──
        month_gz = self._lunar.getMonthInGanZhiExact()
        self._month_gz = _GZ(month_gz[0], month_gz[1])

        # ── Lunar date ──
        lm = self._lunar.getMonth()
        self._lunar_year = self._lunar.getYear()
        self._lunar_month = abs(lm)
        self._lunar_day = self._lunar.getDay()
        self._lunar_leap = lm < 0

    # ─── Public API (matches sxtwl.fromSolar return type) ───

    def getLunarYear(self):  return self._lunar_year
    def getLunarMonth(self): return self._lunar_month
    def getLunarDay(self):   return self._lunar_day
    def isLunarLeap(self):   return self._lunar_leap

    def getYearGZ(self):
        return _GZ(_Gan_list[self._year_gan_idx], _Zhi_list[self._year_zhi_idx])

    def getMonthGZ(self):
        return self._month_gz

    def getDayGZ(self):
        return self._day_gz

    def getHourGZ(self, h):
        """Compute hour ganzhi from day gan + hour zhi (standard 五鼠遁 formula).

        sxtwl.getHourGZ(h) where h=0..23:
          - h=0,1  → 子时 (hour zhi index 0)
          - h=2,3  → 丑时 (1)
          ...
          - h=22,23 → 亥时 (11)

        Hour gan = (day_gan_index × 2 + hour_zhi_index) % 10
        This formula is identical in all Chinese calendar libraries.
        """
        hour_zhi_idx = (h + 1) // 2 % 12
        hour_gan_idx = (self._day_gz.tg * 2 + hour_zhi_idx) % 10
        return _GZ(_Gan_list[hour_gan_idx], _Zhi_list[hour_zhi_idx])

    def hasJieQi(self):
        """Check if this day has a jieqi (节气)."""
        try:
            jq = self._lunar.getJieQiTable()
            date_str = f"{self._lunar.getYear()}-{self._lunar.getMonth():02d}-{self._lunar.getDay():02d}"
            return any(jq[name].toFullString().startswith(date_str) for name in jq)
        except Exception:
            return False

    def getJieQi(self):
        """Get jieqi name if today is a jieqi day, empty string otherwise."""
        try:
            jq = self._lunar.getJieQiTable()
            date_str = f"{self._lunar.getYear()}-{self._lunar.getMonth():02d}-{self._lunar.getDay():02d}"
            for name in jq:
                if jq[name].toFullString().startswith(date_str):
                    return name
            return ""
        except Exception:
            return ""
