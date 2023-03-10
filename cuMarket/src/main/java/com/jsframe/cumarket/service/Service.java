package com.jsframe.cumarket.service;

import com.jsframe.cumarket.entity.Board;
import com.jsframe.cumarket.entity.BoardFile;
import com.jsframe.cumarket.entity.Member;
import com.jsframe.cumarket.repository.BoardFileRepository;
import com.jsframe.cumarket.repository.BoardRepository;

import com.jsframe.cumarket.util.PagingUtil;

import com.jsframe.cumarket.repository.MemberRepository;

import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;


@org.springframework.stereotype.Service
@Log
public class Service {
    ModelAndView mv;

    @Autowired
    private BoardRepository bRepo;

    @Autowired
    private BoardFileRepository bfRepo;

    @Autowired
    private MemberRepository mRepo;

    public ModelAndView getBoard(long bnum) {
        log.info("getBoard()");
        mv = new ModelAndView();

        //게시글 담기
        Board board = bRepo.findById(bnum).get();
        mv.addObject("board", board);

        //첨부파일 담기
        List<BoardFile> bfList = bfRepo.findByBfbid(board);
        mv.addObject("bfList", bfList);
        return mv;

    }

    //리스트에 페이징 처리
    public ModelAndView getBoardList(Integer pageNum, HttpSession session) {
        log.info("getBoardList()");
        mv = new ModelAndView();

        if (pageNum == null) {//처음에 접속했을 때는 pageNum이 넘어오지 않는다.
            pageNum = 1;
        }

        int listCnt = 5;//페이지 당 보여질 게시글의 개수.
        //페이징 조건 생성
        Pageable pb = PageRequest.of((pageNum - 1), listCnt,
                Sort.Direction.DESC, "bnum"); //Sort 정렬, Direction 방향, DESC 내림차순, bnum 기준

        Page<Board> result = bRepo.findByBnumGreaterThan(0L, pb);
        List<Board> bList = result.getContent();

        int totalPage = result.getTotalPages();//전체 페이지 개수

        String paging = getPaging(pageNum, totalPage);


        mv.addObject("bList", bList);
        mv.addObject("paging", paging);

        //현재 보이는 페이지의 번호를 저장.
        session.setAttribute("pageNum", pageNum);

        return mv;
    }

    //페이징 처리 2
    private String getPaging(Integer pageNum, int totalPage) {
        String pageHtml = null;
        int pageCnt = 2;//보여질 페이지 번호 개수
        String listName = "list?";//게시판 분류 이름(현재 없음)

        PagingUtil paging = new PagingUtil(totalPage, pageNum,
                pageCnt, listName);

        pageHtml = paging.makePaging();

        return pageHtml;
    }

    //게시글 수정
    @Transactional
    public String boardUpdate(List<MultipartFile> files,
                              Board board,
                              HttpSession session,
                              RedirectAttributes rttr) {
        log.info("boardUpdate()");
        String msg = null;
        String view = null;

        try {
            bRepo.save(board);//insert, update 겸용.
            fileUpload(files, session, board);//신규 파일 업로드 처리

            msg = "수정 성공";
            view = "redirect:detail?bnum=" + board.getBnum();
        } catch (Exception e) {
            e.printStackTrace();
            msg = "수정 실패";
            view = "redirect:updateFrm?bnum=" + board.getBnum();
        }
        rttr.addFlashAttribute("msg", msg);//메시지 전달.

        return view;
    }


    //회원가입
    @Transactional
    public String insertMember(Member member, RedirectAttributes rttr) {
        log.info("insertMember()");
        String msg = null;
        String view = null;

        Member mem1 = mRepo.findByMid(member.getMid());
        if(mem1 ==null ){
            try {
                mRepo.save(member);//

                view = "redirect:/";//목록 화면으로 돌아가기.
                msg = "회원가입 성공";
            } catch (Exception e) {
                e.printStackTrace();
                view = "redirect:join";
                msg = "회원가입 실패";
            }
        }else {
            view = "redirect:join";
            msg = "이미 존재하는 아이디 입니다.";
        }

        rttr.addFlashAttribute("msg", msg);

        return view;
    }

    //로그인
    public String loginProc(Member member, RedirectAttributes rttr, HttpSession session) {
        ModelAndView mv = new ModelAndView();

        log.info("loginProc()");
        String msg = null;
        String view = null;

        try {
            Member mData = mRepo.findByMid(member.getMid());

            if(mData == null){
                msg = "해당 로그인이 존재하지 않습니다.";
                view = "redirect:login";
            } else {
                //입력한 pwd
                String cPwd = member.getMpwd();
                String getPwd = mData.getMpwd();
                log.info(getPwd);
                log.info(cPwd);

                if (cPwd.equals(getPwd)) {
                    session.setAttribute("loginName", mData.getMname());
                    session.setAttribute("loginId", mData.getMid());
                    msg = "로그인 성공";
                    view = "redirect:/";
                } else {
                    msg = "로그인 실패";
                    view = "redirect:login";
                }
            }

        } catch (Exception e) {

            msg = "로그인 실패";
            view = "redirect:login";
        }


        rttr.addFlashAttribute("msg", msg);
        return view;
    }

    //게시물 등록
    @Transactional
    public String regProc(List<MultipartFile> files,
                          Board board, HttpSession session, RedirectAttributes rttr) {
        log.info("regProc()");
        String msg = null;
        String view = null;
        Member member = null;

        try {

            member = board.getBwriter();
            log.info("이름" + member.getMname());

            bRepo.save(board);
            fileUpload(files, session, board);
            msg = "게시물 등록 성공";
            view = "redirect:list";
        } catch (Exception e) {
            msg = "게시물 등록 실패";
            view = "redirect:register";
        }

        rttr.addFlashAttribute("msg", msg);
        return view;
    }

    //게시글 업로드
    private void fileUpload(List<MultipartFile> files, HttpSession session, Board board) throws Exception {
        log.info("fileUpload()");
        //파일 저장 위치 지정. session을 활용
        String realPath = session.getServletContext().getRealPath("/");
        log.info("realPath : " + realPath);

        //파일 업로드용 폴더를 자동으로 생성하도록 처리
        //업로드용 폴더 : upload
        realPath += "upload/";
        File folder = new File(realPath);
        if (folder.isDirectory() == false) {//폴더가 없을 경우 실행.
            folder.mkdir();//폴더 생성 메소드
        }

        for (MultipartFile mf : files) {
            String orname = mf.getOriginalFilename();//업로드 파일명 가져오기
            if (orname.equals("")) {
                //업로드하는 파일이 없는 상태.
                return;//파일 저장 처리 중지!
            }

            //파일 정보를 저장(to boardfiletbl)
            BoardFile bf = new BoardFile();
            bf.setBfbid(board);
            bf.setBforiname(orname);
            String sysname = System.currentTimeMillis()
                    + orname.substring(orname.lastIndexOf("."));
            bf.setBfsysname(sysname);

            //업로드하는 파일을 upload 폴더에 저장.
            File file = new File(realPath + sysname);

            //파일 저장(upload 폴더)
            mf.transferTo(file);

            //파일 정보를 DB에 저장
            bfRepo.save(bf);
        }
    }

    public ResponseEntity<Resource> fileDownlaod(BoardFile bfile, HttpSession session) throws IOException {
        log.info("fileDownload()");

        //파일 저장경로 구하기
        String realpath = session.getServletContext().getRealPath("/");
        realpath += "upload/" + bfile.getBfsysname();
        InputStreamResource fResource = new InputStreamResource(new FileInputStream(realpath));
        String fileName = URLEncoder.encode(bfile.getBforiname(), "UTF-8");
        return  ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .body(fResource);
    }

    //게시물 삭제
    @Transactional
    public String deleteProc(long bnum, HttpSession session, RedirectAttributes rttr) {
        log.info("deleteProc()");
        String msg = null;
        String view = null;

        Board board = new Board();
        board.setBnum(bnum);


        String realPath = session.getServletContext().getRealPath("/");
        realPath += "upload/";

        List<BoardFile> bfList = bfRepo.findByBfbid(board);

        try {
            for(BoardFile bf : bfList){
                String delPath = realPath += bf.getBfsysname();
                File file = new File(realPath);

                if (file.exists()){
                    file.delete();
                }
            }

            bfRepo.deleteByBfbid(board);

            bRepo.deleteById(bnum);

            msg = "삭제 성공";
            view = "redirect:list";
        } catch (Exception e){
            e.printStackTrace();
            msg = "삭제 실패";
            view = "redirect:detail?bnum=" +bnum;
        }
        rttr.addFlashAttribute("msg", msg);
        return view;

    }

    //게시물 검색
    public ModelAndView searching(Integer pageNum, HttpSession session, String word) {
        log.info("searching()");
        mv = new ModelAndView();
        String msg = word;

        if (pageNum == null) {//처음에 접속했을 때는 pageNum이 넘어오지 않는다.
            pageNum = 1;
        }

        int listCnt = 5;//페이지 당 보여질 게시글의 개수.
        //페이징 조건 생성
        Pageable pb = PageRequest.of((pageNum - 1), listCnt,
                Sort.Direction.DESC, "bnum"); //Sort 정렬, Direction 방향, DESC 내림차순, bnum 기준


        Page<Board> result = bRepo.findByBnumGreaterThan(0L, pb);

        List<Board> cList = bRepo.findByBpname(word);

        List<Board> dList = bRepo.findByBtitle(word);

        int totalPage = result.getTotalPages();//전체 페이지 개수

        String paging = getPaging(pageNum, totalPage);

        if(cList != null && cList.size()!=0){
            mv.addObject("bList", cList);
            mv.addObject("paging", paging);

            session.setAttribute("pageNum", pageNum);

        }else if(dList != null && dList.size()!=0){
            mv.addObject("bList", dList);
            mv.addObject("paging", paging);

            //현재 보이는 페이지의 번호를 저장.
            session.setAttribute("pageNum", pageNum);
        }else {
            mv.addObject("msg",msg);

            //현재 보이는 페이지의 번호를 저장.
            session.setAttribute("pageNum", pageNum);
        }
        return mv;
    }

    public String search(String mid) {
        log.info("중복검사");
        String num = null;


        Member member = null;
        try {
            member = mRepo.findById(mid).get();
            num = "2";
        } catch (Exception e) {
            e.printStackTrace();
            num = "1";
        }

        return num;
    }

}



